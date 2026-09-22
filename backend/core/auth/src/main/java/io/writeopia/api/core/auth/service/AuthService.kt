package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.hash.toBase64
import io.writeopia.api.core.auth.models.LoginResult
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.models.WriteopiaBeUser
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getWorkspacesByUserId
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.api.core.auth.repository.insertUserInWorkspace
import io.writeopia.api.core.auth.repository.updatePassword
import io.writeopia.api.core.auth.repository.insertWorkspace
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import java.util.UUID

object AuthService {
    /**
     * Looks up the user by email, verifies the password and checks account status.
     * Does not produce an HTTP response - that's route-specific (tokens in body vs cookies).
     */
    fun authenticate(
        writeopiaDb: WriteopiaDbBackend,
        credentials: LoginRequest,
        debugMode: Boolean = false
    ): LoginResult {
        val user = writeopiaDb.getUserByEmail(credentials.email)
            ?: return LoginResult.InvalidCredentials

        val isVerified = HashUtils.verifyPassword(
            inputPassword = credentials.password,
            storedHashBase64 = user.password,
            storedSaltBase64 = user.salt
        )

        if (!isVerified) {
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
