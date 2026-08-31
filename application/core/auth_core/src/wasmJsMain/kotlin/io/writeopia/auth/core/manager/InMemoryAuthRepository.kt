@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class InMemoryAuthRepository : AuthRepository {

    private var currentUser: WriteopiaUser = WriteopiaUser.noUser()
    private var currentAccessToken: String? = null
    private var currentRefreshToken: String? = null
    private var accessTokenExpiresAt: Long? = null
    private var currentWorkspace: Workspace? = null
    private var userSelected: Boolean = false

    private var pendingConfirmationEmail: String? = null
    private var forgotPasswordEmail: String? = null
    private var forgotPasswordCode: String? = null

    override suspend fun getUser(): WriteopiaUser = currentUser

    override suspend fun isLoggedIn(): Boolean =
        currentAccessToken.takeIf { it?.isNotEmpty() == true } != null

    override suspend fun logout(): ResultData<Boolean> {
        currentAccessToken = null
        currentRefreshToken = null
        accessTokenExpiresAt = null
        currentUser = WriteopiaUser.noUser()
        currentWorkspace = null
        userSelected = false
        return ResultData.Complete(true)
    }

    override suspend fun saveUser(user: WriteopiaUser, selected: Boolean) {
        currentUser = user
        userSelected = selected
    }

    override suspend fun getAuthToken(): String? = currentAccessToken

    override suspend fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        currentAccessToken = accessToken
        currentRefreshToken = refreshToken
        accessTokenExpiresAt = expiresAt
    }

    override suspend fun getRefreshToken(): String? = currentRefreshToken

    override suspend fun getTokenData(): TokenData? {
        val accessToken = currentAccessToken ?: return null
        return TokenData(
            accessToken = accessToken,
            refreshToken = currentRefreshToken,
            accessTokenExpiresAt = accessTokenExpiresAt
        )
    }

    override suspend fun isAccessTokenExpired(): Boolean {
        val expiresAt = accessTokenExpiresAt ?: return false
        return Clock.System.now().toEpochMilliseconds() >= expiresAt
    }

    override suspend fun clearTokens() {
        currentAccessToken = null
        currentRefreshToken = null
        accessTokenExpiresAt = null
    }

    override suspend fun useOffline() {
        val user = getUser()
        saveUser(user.copy(id = WriteopiaUser.DISCONNECTED), true)
        unselectAllWorkspaces()
        saveWorkspace(Workspace.disconnectedWorkspace().copy(selected = true))
    }

    override suspend fun getWorkspace(): Workspace? = currentWorkspace

    override suspend fun saveWorkspace(workspace: Workspace) {
        currentWorkspace = workspace
    }

    override suspend fun unselectAllWorkspaces() {
        currentWorkspace = currentWorkspace?.copy(selected = false)
    }

    override suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long) {
        if (currentWorkspace?.id == workspaceId) {
            currentWorkspace = currentWorkspace?.copy(lastEventSync = lastEventSync)
        }
    }

    override suspend fun unselectAllUsers() {
        userSelected = false
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
}
