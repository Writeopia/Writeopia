@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.repository

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.SqlDelightAuthRepository
import io.writeopia.auth.core.manager.TokenData
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sql.WriteopiaDb
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow

/**
 * AuthRepository implementation for iOS/macOS that uses Keychain for secure token storage.
 *
 * Delegates user and workspace management to SqlDelightAuthRepository,
 * but overrides token operations to use the iOS Keychain via KeychainTokenStorage.
 */
internal class KeychainAuthRepository(
    writeopiaDb: WriteopiaDb?
) : AuthRepository {

    private val delegate = SqlDelightAuthRepository(writeopiaDb)
    private var migrationChecked = false

    override fun listenForUser(): Flow<WriteopiaUser> = delegate.listenForUser()

    override fun listenForWorkspace(): Flow<Workspace> = delegate.listenForWorkspace()

    override suspend fun getUser(): WriteopiaUser = delegate.getUser()

    override suspend fun isLoggedIn(): Boolean = getAuthToken() != null

    override suspend fun logout(): ResultData<Boolean> {
        val userId = getUser().id
        KeychainTokenStorage.clearTokens(userId)
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
        // Save to Keychain only
        KeychainTokenStorage.saveTokens(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt
        )
    }

    override suspend fun getAuthToken(): String? {
        migrateTokensIfNeeded()
        return KeychainTokenStorage.getAccessToken(getUser().id)
    }

    override suspend fun getRefreshToken(): String? {
        migrateTokensIfNeeded()
        return KeychainTokenStorage.getRefreshToken(getUser().id)
    }

    override suspend fun getTokenData(): TokenData? {
        migrateTokensIfNeeded()
        return KeychainTokenStorage.getTokenData(getUser().id)
    }

    override suspend fun isAccessTokenExpired(): Boolean {
        val tokenData = getTokenData() ?: return true
        val expiresAt = tokenData.accessTokenExpiresAt ?: return false
        return Clock.System.now().toEpochMilliseconds() >= expiresAt
    }

    override suspend fun clearTokens() {
        KeychainTokenStorage.clearTokens(getUser().id)
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

    /**
     * Migrates tokens from legacy SQLDelight storage to secure Keychain.
     * This is a one-time migration that runs on first access after upgrade.
     */
    private suspend fun migrateTokensIfNeeded() {
        if (migrationChecked || KeychainTokenStorage.isMigrationCompleted()) {
            migrationChecked = true
            return
        }

        val userId = getUser().id
        // Get legacy token data from SQLDelight delegate
        val legacyTokenData = delegate.getTokenData()

        if (legacyTokenData != null && !KeychainTokenStorage.hasTokens(userId)) {
            // Migrate tokens from SQLDelight to Keychain
            KeychainTokenStorage.saveTokens(
                userId = userId,
                accessToken = legacyTokenData.accessToken,
                refreshToken = legacyTokenData.refreshToken,
                expiresAt = legacyTokenData.accessTokenExpiresAt
            )

            // Clear tokens from legacy SQLDelight storage
            delegate.clearTokens()
        }

        KeychainTokenStorage.setMigrationCompleted()
        migrationChecked = true
    }
}
