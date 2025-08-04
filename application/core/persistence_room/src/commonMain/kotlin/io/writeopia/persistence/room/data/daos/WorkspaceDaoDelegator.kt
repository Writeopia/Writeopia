package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.WorkspaceCommonDao
import io.writeopia.persistence.room.extensions.toDomain
import io.writeopia.persistence.room.extensions.toEntity
import io.writeopia.sdk.models.Workspace

class WorkspaceDaoDelegator(private val workspaceDao: WorkspaceDao): WorkspaceCommonDao {

    override suspend fun getWorkspace(workspaceId: String): Workspace? =
        workspaceDao.getWorkspaceById(workspaceId)?.toDomain()

    override suspend fun insertWorkspace(
        workspace: Workspace,
        selected: Boolean
    ) {
        workspaceDao.insert(workspace.toEntity(selected))
    }

    override suspend fun deleteWorkspace(workspaceId: String) {

    }

    override suspend fun selectCurrentWorkspace(): Workspace? =
        workspaceDao.selectCurrentWorkspace()?.toDomain()
}
