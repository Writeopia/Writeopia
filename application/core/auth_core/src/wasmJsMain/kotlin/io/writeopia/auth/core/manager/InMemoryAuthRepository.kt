@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlin.time.ExperimentalTime

internal class InMemoryAuthRepository : AuthRepository {

    private var currentUser: WriteopiaUser = WriteopiaUser.disconnectedUser()
    private var currentToken: String? = null
    private var currentWorkspace: Workspace? = null
    private var userSelected: Boolean = false

    private var pendingConfirmationEmail: String? = null
    private var forgotPasswordEmail: String? = null
    private var forgotPasswordCode: String? = null

    override suspend fun getUser(): WriteopiaUser = currentUser

    override suspend fun isLoggedIn(): Boolean =
        currentToken.takeIf { it?.isNotEmpty() == true } != null

    override suspend fun logout(): ResultData<Boolean> {
        currentToken = null
        currentUser = WriteopiaUser.disconnectedUser()
        currentWorkspace = null
        userSelected = false
        return ResultData.Complete(true)
    }

    override suspend fun saveUser(user: WriteopiaUser, selected: Boolean) {
        currentUser = user
        userSelected = selected
    }

    override suspend fun getAuthToken(): String? = currentToken

    override suspend fun saveToken(userId: String, token: String) {
        currentToken = token
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
