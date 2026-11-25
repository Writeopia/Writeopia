package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.hash.toBase64
import io.writeopia.api.core.auth.legal.LegalDocuments
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
import io.writeopia.api.core.auth.repository.recordLegalAcceptance
import io.writeopia.api.core.auth.routing.validateUsername
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
 * 2. A user with the same (Google-verified) email - the identity is linked to that account for
 *    good. An account still waiting for email confirmation is activated, since Google just
 *    verified the same address.
 * 3. Otherwise there is no account yet: without a username and the current legal version the
 *    answer is [GoogleLoginResult.SignUpRequired], carrying the verified ID token so the client
 *    can send it back with them. With both, a new, already-active account is created with a
 *    random unusable password, that username and a default workspace, in one transaction like
 *    registration, and the Terms and Privacy acceptance is recorded.
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
        val idToken: String
        val identity = try {
            idToken = resolveIdToken(request, exchanger)
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
        val name = identity.name?.trim()?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')

        val existing = writeopiaDb.transactionWithResult { findAndLinkUser(writeopiaDb, identity, email) }
        val user = when (existing) {
            is Existing.Found -> existing.user
            Existing.DeletionPending -> return GoogleLoginResult.DeletionPending
            Existing.None -> {
                val username = request.username?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return GoogleLoginResult.SignUpRequired(
                        idToken = idToken,
                        email = email,
                        name = name,
                        suggestedUsername = GoogleUsernameGenerator.generate(email, writeopiaDb::usernameExists),
                    )

                if (request.acceptedLegalVersion != LegalDocuments.VERSION) {
                    return GoogleLoginResult.BadRequest("Terms and Privacy Policy must be accepted")
                }
                try {
                    validateUsername(username)
                } catch (e: IllegalArgumentException) {
                    return GoogleLoginResult.BadRequest(e.message ?: "Invalid username")
                }
                if (writeopiaDb.usernameExists(username)) return GoogleLoginResult.UsernameTaken

                writeopiaDb.transactionWithResult {
                    createUser(
                        writeopiaDb,
                        identity,
                        email,
                        name,
                        username,
                        provisionWorkspaceForNewUser,
                        onWorkspaceProvisioned,
                    )
                }
            }
        }

        val tokenPair = writeopiaDb.generateAndStoreTokens(user.id)
        return GoogleLoginResult.Success(user, tokenPair, created = existing is Existing.None)
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

    private sealed interface Existing {
        data class Found(val user: WriteopiaBeUser) : Existing
        data object DeletionPending : Existing
        data object None : Existing
    }

    /** The account of this Google identity, by its link or by its verified email (then linked). */
    private fun findAndLinkUser(
        writeopiaDb: WriteopiaDbBackend,
        identity: GoogleIdentity,
        email: String,
    ): Existing {
        writeopiaDb.getUserIdByExternalIdentity(PROVIDER_GOOGLE, identity.subject)
            ?.let(writeopiaDb::getUserById)
            ?.let { linked ->
                return if (linked.status == UserStatus.DELETION_PENDING) Existing.DeletionPending else Existing.Found(linked)
            }

        val existing = writeopiaDb.getUserByEmail(email) ?: return Existing.None
        if (existing.status == UserStatus.DELETION_PENDING) return Existing.DeletionPending

        if (existing.status == UserStatus.EMAIL_CONFIRMATION_PENDING) {
            writeopiaDb.enableUserByEmail(email)
            writeopiaDb.clearConfirmationCode(email)
        }
        writeopiaDb.insertExternalIdentity(PROVIDER_GOOGLE, identity.subject, existing.id)
        logger.info("Google identity linked to existing user ${existing.id}")

        return Existing.Found(writeopiaDb.getUserById(existing.id) ?: existing)
    }

    private fun createUser(
        writeopiaDb: WriteopiaDbBackend,
        identity: GoogleIdentity,
        email: String,
        name: String,
        username: String,
        provisionWorkspaceForNewUser: (WriteopiaDbBackend, String, String, String) -> Unit,
        onWorkspaceProvisioned: (String, String) -> Unit,
    ): WriteopiaBeUser {
        val userId = UUID.randomUUID().toString()
        val workspaceId = GenerateId.generate()

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
        writeopiaDb.recordLegalAcceptance(userId, LegalDocuments.VERSION)
        logger.info("Google login created user $userId")

        return writeopiaDb.getUserById(userId)
            ?: error("User $userId vanished inside its own creation transaction")
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
