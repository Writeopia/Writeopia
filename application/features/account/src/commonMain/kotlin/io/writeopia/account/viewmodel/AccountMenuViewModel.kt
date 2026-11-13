package io.writeopia.account.viewmodel

import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AccountMenuViewModel {

    val isLoggedIn: StateFlow<ResultData<Boolean>>

    val availableWorkspaces: StateFlow<ResultData<List<Workspace>>>

    val selectedWorkspace: Flow<Workspace?>

    val usersOfSelectedWorkspace: Flow<ResultData<List<String>>>

    val lastWorkspaceSync: StateFlow<ResultData<String>>

    val exportWorkspaceState: StateFlow<ResultData<Unit>>

    val logoutInProgress: StateFlow<Boolean>

    /** True while the account is being deleted on the backend. */
    val deleteAccountInProgress: StateFlow<Boolean>

    /** True when the last deletion attempt failed. Cleared when a new attempt starts. */
    val deleteAccountFailed: StateFlow<Boolean>

    /** Email of the signed-in user, to be typed again to confirm the deletion. */
    val userEmail: StateFlow<String>

    /** Easter egg: creates the document about the writer of the version and reports it. */
    fun createVersionEasterEgg(onCreated: (id: String, title: String) -> Unit)

    fun logout(onLogOutSuccess: () -> Unit)

    /** Deletes the account and clears the local session. [onSuccess] runs only when it worked. */
    fun deleteAccount(onSuccess: () -> Unit)

    fun changeWorkspace(onChangeSuccess: () -> Unit)

    fun syncWorkspace()

    fun selectWorkspace(workspaceId: String)

    fun addUserToWorkspace(username: String)

    fun exportWorkspace(workspaceId: String)

    fun resetExportState()
}
