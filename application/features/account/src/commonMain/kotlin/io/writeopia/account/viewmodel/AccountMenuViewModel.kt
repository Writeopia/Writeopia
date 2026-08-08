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

    fun logout(onLogOutSuccess: () -> Unit)

    fun changeWorkspace(onChangeSuccess: () -> Unit)

    fun syncWorkspace()

    fun selectWorkspace(workspaceId: String)

    fun addUserToWorkspace(userEmail: String)
}
