@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.browser.localStorage
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal class LocalStorageAuthRepository : AuthRepository {

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
        localStorage.removeItem(KEY_TOKEN)
        localStorage.removeItem(KEY_USER_ID)
        localStorage.removeItem(KEY_USER_EMAIL)
        localStorage.removeItem(KEY_USER_NAME)
        localStorage.removeItem(KEY_USER_TIER)
        localStorage.removeItem(KEY_USER_SELECTED)
        localStorage.removeItem(KEY_WORKSPACE_ID)
        localStorage.removeItem(KEY_WORKSPACE_USER_ID)
        localStorage.removeItem(KEY_WORKSPACE_NAME)
        localStorage.removeItem(KEY_WORKSPACE_LAST_SYNC)
        localStorage.removeItem(KEY_WORKSPACE_SELECTED)
        localStorage.removeItem(KEY_WORKSPACE_ROLE)

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
        localStorage.getItem(KEY_TOKEN)

    override suspend fun saveToken(userId: String, token: String) {
        localStorage.setItem(KEY_TOKEN, token)
    }

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
        localStorage.setItem(KEY_FORGOT_PASSWORD_EMAIL, email)
    }

    override suspend fun getForgotPasswordEmail(): String? =
        localStorage.getItem(KEY_FORGOT_PASSWORD_EMAIL)

    override suspend fun saveForgotPasswordCode(code: String) {
        localStorage.setItem(KEY_FORGOT_PASSWORD_CODE, code)
    }

    override suspend fun getForgotPasswordCode(): String? =
        localStorage.getItem(KEY_FORGOT_PASSWORD_CODE)

    override suspend fun clearForgotPasswordData() {
        localStorage.removeItem(KEY_FORGOT_PASSWORD_EMAIL)
        localStorage.removeItem(KEY_FORGOT_PASSWORD_CODE)
    }

    companion object {
        private const val KEY_TOKEN = "writeopia_auth_token"
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
        private const val KEY_FORGOT_PASSWORD_EMAIL = "writeopia_forgot_password_email"
        private const val KEY_FORGOT_PASSWORD_CODE = "writeopia_forgot_password_code"
    }
}
