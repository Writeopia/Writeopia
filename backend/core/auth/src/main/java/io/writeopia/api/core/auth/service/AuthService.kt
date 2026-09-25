package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.hash.toBase64
import io.writeopia.api.core.auth.models.LoginResult
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.models.WriteopiaBeUser
import io.writeopia.api.core.auth.repository.getUserByUsernameOrEmail
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.api.core.auth.repository.updatePassword
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import java.util.UUID

object AuthService {
    /**
     * Looks up the user by username or email, verifies the password and checks account status.
     * Does not produce an HTTP response - that's route-specific (tokens in body vs cookies).
     */
    fun authenticate(
        writeopiaDb: WriteopiaDbBackend,
        credentials: LoginRequest,
        debugMode: Boolean = false
    ): LoginResult {
        val identifier = credentials.identifier.trim()
        // Emails are stored lowercased (see registration); usernames are case-sensitive, so
        // only normalize the case when the identifier looks like an email.
        val lookupIdentifier = if (identifier.contains('@')) identifier.lowercase() else identifier
        val user = writeopiaDb.getUserByUsernameOrEmail(lookupIdentifier)

        // Equalize verification timing against unknown identifiers: always run the (expensive)
        // hash comparison, falling back to a dummy hash/salt when there's no real user, so an
        // unknown identifier can't be distinguished from a wrong password by response time.
        val hash = user?.password ?: HashUtils.DUMMY_HASH_BASE64
        val salt = user?.salt ?: HashUtils.DUMMY_SALT_BASE64

        val isVerified = HashUtils.verifyPassword(
            inputPassword = credentials.password,
            storedHashBase64 = hash,
            storedSaltBase64 = salt
        )

        if (user == null || !isVerified) {
            return LoginResult.InvalidCredentials
        }

        return when {
            user.status == UserStatus.ACTIVE || debugMode -> {
                val tokenPair = with(RefreshTokenService) {
                    writeopiaDb.generateAndStoreTokens(user.id)
                }
                LoginResult.Success(user, tokenPair)
            }

            // Distinct from NotConfirmed - reusing that shape here would make the
            // client show a confirmation-code UI to an account that's actually being deleted.
            user.status == UserStatus.DELETION_PENDING -> LoginResult.DeletionPending

            else -> LoginResult.NotConfirmed(user)
        }
    }


    fun createUser(
        writeopiaDb: WriteopiaDbBackend,
        registerRequest: RegisterRequest,
        status: UserStatus
    ): WriteopiaUser {
        val (name, email, username, workspaceName, password) = registerRequest

        val id = UUID.randomUUID().toString()

        val salt = HashUtils.generateSalt()
        val hash = HashUtils.hashPassword(password, salt).toBase64()

        writeopiaDb.insertUser(
            id = id,
            name = name,
            username = username,
            email = email,
            password = hash,
            salt = salt.toBase64(),
            status = status
        )

        return WriteopiaUser(
            id = id,
            name = name,
            email = email,
        )
    }

    fun resetPassword(
        writeopiaDb: WriteopiaDbBackend,
        user: WriteopiaBeUser,
        newPassword: String
    ) {
        val salt = HashUtils.generateSalt()
        val hash = HashUtils.hashPassword(newPassword, salt).toBase64()

        writeopiaDb.updatePassword(user.id, hash, salt.toBase64())
    }
}
