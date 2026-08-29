@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.repository

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.SqlDelightAuthRepository
import io.writeopia.auth.core.manager.TokenData
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sql.WriteopiaDb
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * AuthRepository implementation for Desktop/JVM that uses encrypted storage for tokens.
 *
 * Delegates user and workspace management to SqlDelightAuthRepository,
 * but overrides token operations to use EncryptedTokenStorage with AES-256-GCM encryption.
 *
 * Tokens are encrypted with a machine-specific key, providing protection against:
 * - Unauthorized access to the database file
 * - Token theft when database is copied to another machine
 */
internal class EncryptedAuthRepository(
    writeopiaDb: WriteopiaDb?
) : AuthRepository {

    private val delegate = SqlDelightAuthRepository(writeopiaDb)

    override fun listenForUser(): Flow<WriteopiaUser> = delegate.listenForUser()

    override fun listenForWorkspace(): Flow<Workspace> = delegate.listenForWorkspace()

    override suspend fun getUser(): WriteopiaUser = delegate.getUser()

    override suspend fun isLoggedIn(): Boolean = getAuthToken() != null

    override suspend fun logout(): ResultData<Boolean> {
        val userId = getUser().id
        EncryptedTokenStorage.clearTokens(userId)
        return delegate.logout()
    }

    override suspend fun saveUser(user: WriteopiaUser, selected: Boolean) {
        delegate.saveUser(user, selected)
    }

    override suspend fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        EncryptedTokenStorage.saveTokens(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt
        )
    }

    override suspend fun getAuthToken(): String? =
        EncryptedTokenStorage.getAccessToken(getUser().id)

    override suspend fun getRefreshToken(): String? =
        EncryptedTokenStorage.getRefreshToken(getUser().id)

    override suspend fun getTokenData(): TokenData? =
        EncryptedTokenStorage.getTokenData(getUser().id)

    override suspend fun isAccessTokenExpired(): Boolean {
        val tokenData = getTokenData() ?: return true
        val expiresAt = tokenData.accessTokenExpiresAt ?: return false
        return Clock.System.now().toEpochMilliseconds() >= expiresAt
    }

    override suspend fun clearTokens() {
        EncryptedTokenStorage.clearTokens(getUser().id)
    }

    override suspend fun useOffline() {
        delegate.useOffline()
    }

    override suspend fun getWorkspace(): Workspace? = delegate.getWorkspace()

    override suspend fun saveWorkspace(workspace: Workspace) {
        delegate.saveWorkspace(workspace)
    }

    override suspend fun unselectAllWorkspaces() {
        delegate.unselectAllWorkspaces()
    }

    override suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long) {
        delegate.updateLastEventSync(workspaceId, lastEventSync)
    }

    override suspend fun unselectAllUsers() {
        delegate.unselectAllUsers()
    }

    override suspend fun savePendingConfirmationEmail(email: String) {
        delegate.savePendingConfirmationEmail(email)
    }

    override suspend fun getPendingConfirmationEmail(): String? =
        delegate.getPendingConfirmationEmail()

    override suspend fun clearPendingConfirmationEmail() {
        delegate.clearPendingConfirmationEmail()
    }

    override suspend fun saveForgotPasswordEmail(email: String) {
        delegate.saveForgotPasswordEmail(email)
    }

    override suspend fun getForgotPasswordEmail(): String? = delegate.getForgotPasswordEmail()

    override suspend fun saveForgotPasswordCode(code: String) {
        delegate.saveForgotPasswordCode(code)
    }

    override suspend fun getForgotPasswordCode(): String? = delegate.getForgotPasswordCode()

    override suspend fun clearForgotPasswordData() {
        delegate.clearForgotPasswordData()
    }
}
