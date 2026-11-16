package io.writeopia.common.utils.persistence.daos

import io.writeopia.sdk.models.workspace.Workspace

interface WorkspaceCommonDao {

    suspend fun getWorkspace(workspaceId: String): Workspace?

    suspend fun insertWorkspace(workspace: Workspace, selected: Boolean)

    suspend fun deleteWorkspace(workspaceId: String)

    suspend fun selectCurrentWorkspace(): Workspace?

    suspend fun workspacesOfUser(userId: String): List<Workspace>

    /** Refreshes the names of the workspaces, keeping the state of the known ones. */
    suspend fun cacheWorkspaces(userId: String, workspaces: List<Workspace>)

    suspend fun unselectAllWorkspaces()

    suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long)
}
