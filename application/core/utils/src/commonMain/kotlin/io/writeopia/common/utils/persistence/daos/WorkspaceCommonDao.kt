package io.writeopia.common.utils.persistence.daos

import io.writeopia.sdk.models.workspace.Workspace

interface WorkspaceCommonDao {

    suspend fun getWorkspace(workspaceId: String): Workspace?

    suspend fun insertWorkspace(workspace: Workspace, selected: Boolean)

    suspend fun deleteWorkspace(workspaceId: String)

    suspend fun selectCurrentWorkspace(): Workspace?

    suspend fun unselectAllWorkspaces()
}
