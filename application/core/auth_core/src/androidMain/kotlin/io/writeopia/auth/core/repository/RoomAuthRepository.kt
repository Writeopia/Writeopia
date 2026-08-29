@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.repository

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.TokenData
import io.writeopia.common.utils.persistence.daos.TokenCommonDao
import io.writeopia.common.utils.persistence.daos.UserCommonDao
import io.writeopia.common.utils.persistence.daos.WorkspaceCommonDao
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class RoomAuthRepository(
    private val userDao: UserCommonDao,
    private val tokenCommonDao: TokenCommonDao,
    private val workspaceCommonDao: WorkspaceCommonDao,
    private val secureTokenStorage: SecureTokenStorage
) : AuthRepository {

    private var pendingConfirmationEmail: String? = null
    private var forgotPasswordEmail: String? = null
    private var forgotPasswordCode: String? = null
    private var migrationChecked = false

    override suspend fun getUser(): WriteopiaUser = userDao.selectedCurrentUser()

    override suspend fun isLoggedIn(): Boolean = getAuthToken() != null

    override suspend fun logout(): ResultData<Boolean> {
        val userId = getUser().id
        secureTokenStorage.clearTokens(userId)
        unselectAllWorkspaces()
        unselectAllUsers()

        return ResultData.Complete(true)
    }

    override suspend fun saveUser(user: WriteopiaUser, selected: Boolean) {
        userDao.insertUser(user, selected)
    }

    override suspend fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        // Save to secure storage only
        secureTokenStorage.saveTokens(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt
        )
    }

    override suspend fun getAuthToken(): String? {
        migrateTokensIfNeeded()
        return secureTokenStorage.getAccessToken(getUser().id)
    }

    override suspend fun getRefreshToken(): String? {
        migrateTokensIfNeeded()
        return secureTokenStorage.getRefreshToken(getUser().id)
    }

    override suspend fun getTokenData(): TokenData? {
        migrateTokensIfNeeded()
        return secureTokenStorage.getTokenData(getUser().id)
    }

    override suspend fun isAccessTokenExpired(): Boolean {
        val tokenData = getTokenData() ?: return true
        val expiresAt = tokenData.accessTokenExpiresAt ?: return false
        return Clock.System.now().toEpochMilliseconds() >= expiresAt
    }

    override suspend fun clearTokens() {
        secureTokenStorage.clearTokens(getUser().id)
    }

    override suspend fun useOffline() {
        unselectAllUsers()
        saveUser(WriteopiaUser.disconnectedUser().copy(id = WriteopiaUser.DISCONNECTED), true)

        unselectAllWorkspaces()
        saveWorkspace(Workspace.disconnectedWorkspace().copy(selected = true))
    }

    override suspend fun getWorkspace(): Workspace? = workspaceCommonDao.selectCurrentWorkspace()

    override suspend fun saveWorkspace(workspace: Workspace) {
        workspaceCommonDao.insertWorkspace(workspace, true)
    }

    override suspend fun unselectAllWorkspaces() {
        workspaceCommonDao.unselectAllWorkspaces()
    }

    override suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long) {
        workspaceCommonDao.updateLastEventSync(workspaceId, lastEventSync)
    }

    override suspend fun unselectAllUsers() {
        userDao.unselectAllUsers()
    }

    override suspend fun savePendingConfirmationEmail(email: String) {
        pendingConfirmationEmail = email
    }

    override suspend fun getPendingConfirmationEmail(): String? = pendingConfirmationEmail

    override suspend fun clearPendingConfirmationEmail() {
        pendingConfirmationEmail = null
    }

    override suspend fun saveForgotPasswordEmail(email: String) {
        forgotPasswordEmail = email
    }

    override suspend fun getForgotPasswordEmail(): String? = forgotPasswordEmail

    override suspend fun saveForgotPasswordCode(code: String) {
        forgotPasswordCode = code
    }

    override suspend fun getForgotPasswordCode(): String? = forgotPasswordCode

    override suspend fun clearForgotPasswordData() {
        forgotPasswordEmail = null
        forgotPasswordCode = null
    }

    /**
     * Migrates tokens from legacy Room storage to secure EncryptedSharedPreferences.
     * This is a one-time migration that runs on first access after upgrade.
     */
    private suspend fun migrateTokensIfNeeded() {
        if (migrationChecked || secureTokenStorage.isMigrationCompleted()) {
            migrationChecked = true
            return
        }

        val userId = getUser().id
        val legacyTokenDetails = tokenCommonDao.getTokenDetails(userId)

        if (legacyTokenDetails != null && !secureTokenStorage.hasTokens(userId)) {
            // Migrate tokens from Room to encrypted storage
            secureTokenStorage.saveTokens(
                userId = userId,
                accessToken = legacyTokenDetails.accessToken,
                refreshToken = legacyTokenDetails.refreshToken,
                expiresAt = legacyTokenDetails.accessTokenExpiresAt
            )

            // Delete tokens from legacy Room storage
            tokenCommonDao.deleteToken(userId)
        }

        secureTokenStorage.setMigrationCompleted()
        migrationChecked = true
    }
}
