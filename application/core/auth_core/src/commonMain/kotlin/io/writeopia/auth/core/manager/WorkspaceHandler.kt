package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WorkspaceHandler {

    val isAutoSyncEnabled: StateFlow<Boolean>

    /**
     * Emits when local workspace files have changed and should be re-read into the database.
     */
    val localSyncRequired: Flow<Unit>
    val availableWorkspaces: StateFlow<ResultData<List<Workspace>>>

    val selectedWorkspace: Flow<Workspace?>

    val usersOfSelectedWorkspace: Flow<ResultData<List<String>>>

    val lastWorkspaceSync: StateFlow<ResultData<String>>

    val workspaceLocalPath: StateFlow<String>

    fun initScope(coroutineScope: CoroutineScope)

    fun loadAvailableWorkspaces()

    fun selectWorkspaceToManage(workspaceId: String)

    fun syncWorkspace()

    fun addUserToWorkspace(userEmail: String)

    fun changeWorkspaceLocalPath(path: String)

    fun initWorkspacePath()

    fun startAutoSync()

    fun stopAutoSync()
}
