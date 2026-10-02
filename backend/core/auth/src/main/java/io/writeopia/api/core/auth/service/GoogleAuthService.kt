package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.hash.toBase64
import io.writeopia.api.core.auth.models.GoogleIdentity
import io.writeopia.api.core.auth.models.GoogleLoginResult
import io.writeopia.api.core.auth.models.GoogleTokenException
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.models.WriteopiaBeUser
import io.writeopia.api.core.auth.repository.PROVIDER_GOOGLE
import io.writeopia.api.core.auth.repository.clearConfirmationCode
import io.writeopia.api.core.auth.repository.enableUserByEmail
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.getUserIdByExternalIdentity
import io.writeopia.api.core.auth.repository.insertExternalIdentity
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.api.core.auth.repository.usernameExists
import io.writeopia.api.core.auth.service.RefreshTokenService.generateAndStoreTokens
import io.writeopia.connection.logger
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.serialization.data.auth.GoogleLoginRequest
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID

/**
 * "Sign in with Google": resolves the request to a verified [GoogleIdentity], then finds or
 * creates the Writeopia user and issues the regular token pair.
 *
 * Resolution order:
 * 1. An `external_identity` row for Google's subject id - the normal repeat-login path.
 * 2. A user with the same (Google-verified) email - the identity is linked to that account.
 *    An account still waiting for email confirmation is activated, since Google just verified
 *    the same address.
 * 3. Otherwise a new, already-active account is created with a random unusable password, a
 *    generated username and a default workspace, in one transaction like registration.
 */
object GoogleAuthService {
    private const val DEFAULT_WORKSPACE_NAME = "My Workspace"
    private const val WORKSPACE_NAME_MAX_LENGTH = 30
    private const val WORKSPACE_NAME_MIN_LENGTH = 3

    suspend fun login(
        writeopiaDb: WriteopiaDbBackend,
        request: GoogleLoginRequest,
        verifier: GoogleTokenVerifier,
        exchanger: GoogleCodeExchanger,
        provisionWorkspaceForNewUser: (
            writeopiaDb: WriteopiaDbBackend,
            workspaceId: String,
            workspaceName: String,
            userId: String
        ) -> Unit,
        onWorkspaceProvisioned: (userId: String, workspaceId: String) -> Unit,
    ): GoogleLoginResult {
        val identity = try {
            val idToken = resolveIdToken(request, exchanger)
                ?: return GoogleLoginResult.BadRequest("idToken or code is required")
            verifier.verify(idToken)
        } catch (e: GoogleTokenException) {
            logger.info("Google login rejected: ${e.message}")
            return GoogleLoginResult.InvalidToken(e.message ?: "Invalid Google token")
        }

        if (!identity.emailVerified) {
            return GoogleLoginResult.InvalidToken("Google account email is not verified")
        }

        val email = identity.email.trim().lowercase()
        val workspaceId = GenerateId.generate()

        val resolved: Pair<WriteopiaBeUser, Boolean>? = writeopiaDb.transactionWithResult {
            findOrCreateUser(
                writeopiaDb,
                identity,
                email,
                workspaceId,
                provisionWorkspaceForNewUser,
                onWorkspaceProvisioned
            )
        }
        val (user, created) = resolved ?: return GoogleLoginResult.DeletionPending

        val tokenPair = writeopiaDb.generateAndStoreTokens(user.id)
        return GoogleLoginResult.Success(user, tokenPair, created)
    }

    private suspend fun resolveIdToken(request: GoogleLoginRequest, exchanger: GoogleCodeExchanger): String? {
        request.idToken?.takeIf { it.isNotBlank() }?.let { return it }

        val code = request.code?.takeIf { it.isNotBlank() } ?: return null
        val redirectUri = request.redirectUri?.takeIf { it.isNotBlank() }
            ?: throw GoogleTokenException("redirectUri is required to exchange a code")
        val clientId = request.clientId?.takeIf { it.isNotBlank() }
            ?: throw GoogleTokenException("clientId is required to exchange a code")

        return exchanger.exchange(code, request.codeVerifier, redirectUri, clientId)
    }

    /** Returns null when the matching account is pending deletion and must not be used. */
    private fun findOrCreateUser(
        writeopiaDb: WriteopiaDbBackend,
        identity: GoogleIdentity,
        email: String,
        workspaceId: String,
        provisionWorkspaceForNewUser: (WriteopiaDbBackend, String, String, String) -> Unit,
        onWorkspaceProvisioned: (String, String) -> Unit,
    ): Pair<WriteopiaBeUser, Boolean>? {
        writeopiaDb.getUserIdByExternalIdentity(PROVIDER_GOOGLE, identity.subject)
            ?.let(writeopiaDb::getUserById)
            ?.let { linked ->
                return if (linked.status == UserStatus.DELETION_PENDING) null else linked to false
            }

        writeopiaDb.getUserByEmail(email)?.let { existing ->
            if (existing.status == UserStatus.DELETION_PENDING) return null

            if (existing.status == UserStatus.EMAIL_CONFIRMATION_PENDING) {
                writeopiaDb.enableUserByEmail(email)
                writeopiaDb.clearConfirmationCode(email)
            }
            writeopiaDb.insertExternalIdentity(PROVIDER_GOOGLE, identity.subject, existing.id)
            logger.info("Google identity linked to existing user ${existing.id}")

            val refreshed = writeopiaDb.getUserById(existing.id) ?: existing
            return refreshed to false
        }

        val userId = UUID.randomUUID().toString()
        val name = identity.name?.trim()?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')
        val username = GoogleUsernameGenerator.generate(email, writeopiaDb::usernameExists)

        // Google accounts never log in with a password, but the column is NOT NULL: store a hash of
        // random bytes nobody knows, so password login for this email can't succeed by accident.
        val salt = HashUtils.generateSalt()
        val unusablePassword = HashUtils.generateSalt().toBase64()
        val passwordHash = HashUtils.hashPassword(unusablePassword, salt).toBase64()

        writeopiaDb.insertUser(
            id = userId,
            name = name,
            username = username,
            email = email,
            password = passwordHash,
            salt = salt.toBase64(),
            status = UserStatus.ACTIVE,
        )

        provisionWorkspaceForNewUser(writeopiaDb, workspaceId, defaultWorkspaceName(name), userId)
        onWorkspaceProvisioned(userId, workspaceId)

        writeopiaDb.insertExternalIdentity(PROVIDER_GOOGLE, identity.subject, userId)
        logger.info("Google login created user $userId")

        val user = writeopiaDb.getUserById(userId)
            ?: error("User $userId vanished inside its own creation transaction")
        return user to true
    }

    internal fun defaultWorkspaceName(name: String): String {
        val firstName = name.trim().substringBefore(' ').trim()
        if (firstName.isEmpty()) return DEFAULT_WORKSPACE_NAME

        val candidate = "$firstName's Workspace"
        val clipped = if (candidate.length > WORKSPACE_NAME_MAX_LENGTH) {
            candidate.take(WORKSPACE_NAME_MAX_LENGTH).trimEnd()
        } else {
            candidate
        }
        return if (clipped.length < WORKSPACE_NAME_MIN_LENGTH) DEFAULT_WORKSPACE_NAME else clipped
    }
}
