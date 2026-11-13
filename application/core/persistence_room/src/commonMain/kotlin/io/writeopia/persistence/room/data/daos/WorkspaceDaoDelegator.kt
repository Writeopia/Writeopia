package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.WorkspaceCommonDao
import io.writeopia.persistence.room.extensions.toDomain
import io.writeopia.persistence.room.extensions.toEntity
import io.writeopia.sdk.models.workspace.Workspace
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
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
        workspaceDao.currentWorkspace()?.toDomain()

    override suspend fun workspacesOfUser(userId: String): List<Workspace> =
        workspaceDao.workspacesOfUser(userId).map { it.toDomain() }

    override suspend fun cacheWorkspaces(userId: String, workspaces: List<Workspace>) {
        workspaceDao.cacheNames(
            userId,
            workspaces.map { workspace ->
                workspace.copy(userId = userId, lastSync = Instant.fromEpochMilliseconds(0))
                    .toEntity(selected = false)
            }
        )
    }

    override suspend fun unselectAllWorkspaces() {
        workspaceDao.unselectAllWorkspaces()
    }

    override suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long) {
        workspaceDao.updateLastEventSync(workspaceId, lastEventSync)
    }
}
