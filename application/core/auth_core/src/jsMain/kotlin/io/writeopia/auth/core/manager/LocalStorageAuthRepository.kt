@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.browser.localStorage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal class LocalStorageAuthRepository : AuthRepository {

    // Session-scoped storage for sensitive reset flow data (not persisted to localStorage)
    private var forgotPasswordEmail: String? = null
    private var forgotPasswordCode: String? = null

    override suspend fun getUser(): WriteopiaUser {
        val userId = localStorage.getItem(KEY_USER_ID) ?: return WriteopiaUser.disconnectedUser()
        val email = localStorage.getItem(KEY_USER_EMAIL) ?: ""
        val name = localStorage.getItem(KEY_USER_NAME) ?: ""
        val tierName = localStorage.getItem(KEY_USER_TIER) ?: Tier.FREE.name

        return WriteopiaUser(
            id = userId,
            email = email,
            name = name,
            tier = Tier.entries.find { it.name == tierName } ?: Tier.FREE
        )
    }

    override suspend fun isLoggedIn(): Boolean =
        getAuthToken().takeIf { it?.isNotEmpty() == true } != null

    override suspend fun logout(): ResultData<Boolean> {
        clearTokens()
        localStorage.removeItem(KEY_USER_ID)
        localStorage.removeItem(KEY_USER_EMAIL)
        localStorage.removeItem(KEY_USER_NAME)
        localStorage.removeItem(KEY_USER_TIER)
        localStorage.removeItem(KEY_USER_SELECTED)
        localStorage.removeItem(KEY_WORKSPACE_ID)
        localStorage.removeItem(KEY_WORKSPACE_USER_ID)
        localStorage.removeItem(KEY_WORKSPACE_NAME)
        localStorage.removeItem(KEY_WORKSPACE_LAST_SYNC)
        localStorage.removeItem(KEY_WORKSPACE_LAST_EVENT_SYNC)
        localStorage.removeItem(KEY_WORKSPACE_SELECTED)
        localStorage.removeItem(KEY_WORKSPACE_ROLE)
        clearForgotPasswordData()
        clearPendingConfirmationEmail()

        return ResultData.Complete(true)
    }

    override suspend fun saveUser(user: WriteopiaUser, selected: Boolean) {
        localStorage.setItem(KEY_USER_ID, user.id)
        localStorage.setItem(KEY_USER_EMAIL, user.email)
        localStorage.setItem(KEY_USER_NAME, user.name)
        localStorage.setItem(KEY_USER_TIER, user.tier.name)
        localStorage.setItem(KEY_USER_SELECTED, selected.toString())
    }

    override suspend fun getAuthToken(): String? =
        localStorage.getItem(KEY_ACCESS_TOKEN)

    override suspend fun useOffline() {
        val user = getUser()
        saveUser(user.copy(id = WriteopiaUser.DISCONNECTED), true)
        unselectAllWorkspaces()
        saveWorkspace(Workspace.disconnectedWorkspace().copy(selected = true))
    }

    override suspend fun getWorkspace(): Workspace? {
        val id = localStorage.getItem(KEY_WORKSPACE_ID) ?: return null
        val userId = localStorage.getItem(KEY_WORKSPACE_USER_ID) ?: return null
        val name = localStorage.getItem(KEY_WORKSPACE_NAME) ?: ""
        val lastSyncMs = localStorage.getItem(KEY_WORKSPACE_LAST_SYNC)?.toLongOrNull() ?: 0L
        val lastEventSync = localStorage.getItem(KEY_WORKSPACE_LAST_EVENT_SYNC)?.toLongOrNull() ?: 0L
        val selected = localStorage.getItem(KEY_WORKSPACE_SELECTED)?.toBoolean() ?: false
        val role = localStorage.getItem(KEY_WORKSPACE_ROLE) ?: ""

        return Workspace(
            id = id,
            userId = userId,
            name = name,
            lastSync = Instant.fromEpochMilliseconds(lastSyncMs),
            lastEventSync = lastEventSync,
            selected = selected,
            role = role
        )
    }

    override suspend fun saveWorkspace(workspace: Workspace) {
        localStorage.setItem(KEY_WORKSPACE_ID, workspace.id)
        localStorage.setItem(KEY_WORKSPACE_USER_ID, workspace.userId)
        localStorage.setItem(KEY_WORKSPACE_NAME, workspace.name)
        localStorage.setItem(KEY_WORKSPACE_LAST_SYNC, workspace.lastSync.toEpochMilliseconds().toString())
        localStorage.setItem(KEY_WORKSPACE_LAST_EVENT_SYNC, workspace.lastEventSync.toString())
        localStorage.setItem(KEY_WORKSPACE_SELECTED, workspace.selected.toString())
        localStorage.setItem(KEY_WORKSPACE_ROLE, workspace.role)
    }

    override suspend fun unselectAllWorkspaces() {
        localStorage.setItem(KEY_WORKSPACE_SELECTED, false.toString())
    }

    override suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long) {
        localStorage.setItem(KEY_WORKSPACE_LAST_EVENT_SYNC, lastEventSync.toString())
    }

    override suspend fun unselectAllUsers() {
        localStorage.setItem(KEY_USER_SELECTED, false.toString())
    }

    override suspend fun savePendingConfirmationEmail(email: String) {
        localStorage.setItem(KEY_PENDING_CONFIRMATION_EMAIL, email)
    }

    override suspend fun getPendingConfirmationEmail(): String? =
        localStorage.getItem(KEY_PENDING_CONFIRMATION_EMAIL)

    override suspend fun clearPendingConfirmationEmail() {
        localStorage.removeItem(KEY_PENDING_CONFIRMATION_EMAIL)
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

    override suspend fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        localStorage.setItem(KEY_ACCESS_TOKEN, accessToken)
        refreshToken?.let { localStorage.setItem(KEY_REFRESH_TOKEN, it) }
            ?: localStorage.removeItem(KEY_REFRESH_TOKEN)
        expiresAt?.let { localStorage.setItem(KEY_ACCESS_TOKEN_EXPIRES_AT, it.toString()) }
            ?: localStorage.removeItem(KEY_ACCESS_TOKEN_EXPIRES_AT)
    }

    override suspend fun getRefreshToken(): String? =
        localStorage.getItem(KEY_REFRESH_TOKEN)

    override suspend fun getTokenData(): TokenData? {
        val accessToken = localStorage.getItem(KEY_ACCESS_TOKEN) ?: return null
        return TokenData(
            accessToken = accessToken,
            refreshToken = localStorage.getItem(KEY_REFRESH_TOKEN),
            accessTokenExpiresAt = localStorage.getItem(KEY_ACCESS_TOKEN_EXPIRES_AT)?.toLongOrNull()
        )
    }

    override suspend fun isAccessTokenExpired(): Boolean {
        val expiresAt = localStorage.getItem(KEY_ACCESS_TOKEN_EXPIRES_AT)?.toLongOrNull() ?: return false
        return Clock.System.now().toEpochMilliseconds() >= expiresAt
    }

    override suspend fun clearTokens() {
        localStorage.removeItem(KEY_ACCESS_TOKEN)
        localStorage.removeItem(KEY_REFRESH_TOKEN)
        localStorage.removeItem(KEY_ACCESS_TOKEN_EXPIRES_AT)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "writeopia_access_token"
        private const val KEY_REFRESH_TOKEN = "writeopia_refresh_token"
        private const val KEY_ACCESS_TOKEN_EXPIRES_AT = "writeopia_access_token_expires_at"
        private const val KEY_USER_ID = "writeopia_user_id"
        private const val KEY_USER_EMAIL = "writeopia_user_email"
        private const val KEY_USER_NAME = "writeopia_user_name"
        private const val KEY_USER_TIER = "writeopia_user_tier"
        private const val KEY_USER_SELECTED = "writeopia_user_selected"
        private const val KEY_WORKSPACE_ID = "writeopia_workspace_id"
        private const val KEY_WORKSPACE_USER_ID = "writeopia_workspace_user_id"
        private const val KEY_WORKSPACE_NAME = "writeopia_workspace_name"
        private const val KEY_WORKSPACE_LAST_SYNC = "writeopia_workspace_last_sync"
        private const val KEY_WORKSPACE_LAST_EVENT_SYNC = "writeopia_workspace_last_event_sync"
        private const val KEY_WORKSPACE_SELECTED = "writeopia_workspace_selected"
        private const val KEY_WORKSPACE_ROLE = "writeopia_workspace_role"
        private const val KEY_PENDING_CONFIRMATION_EMAIL = "writeopia_pending_confirmation_email"
    }
}
