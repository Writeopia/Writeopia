package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.FolderCommonDao
import io.writeopia.common.utils.persistence.folder.toModel
import io.writeopia.common.utils.persistence.folder.toRoomEntity
import io.writeopia.sdk.models.document.Folder
import io.writeopia.persistence.room.data.entities.FolderContentSyncEntity
import io.writeopia.persistence.room.extensions.toEntity
import io.writeopia.persistence.room.extensions.toCommonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FolderDaoDelegator(
    private val delegate: FolderRoomDao
) : FolderCommonDao {

    override suspend fun upsertFolder(folderEntity: Folder) {
        delegate.upsertFolder(folderEntity.toRoomEntity().toEntity())
    }

    override suspend fun getFolderById(id: String): Folder? =
        delegate.getFolderById(id)?.toCommonEntity()?.toModel(0)

    override suspend fun search(query: String, workspaceId: String): List<Folder> =
        delegate.search(query, workspaceId).map { it.toCommonEntity().toModel(0) }

    override suspend fun getFoldersForWorkspace(workspaceId: String): List<Folder> =
        delegate.getFoldersByWorkspaceId(workspaceId).map { it.toCommonEntity().toModel(0) }

    override suspend fun getOutdatedFoldersForWorkspace(workspaceId: String): List<Folder> =
        delegate.getOutdatedFoldersByWorkspaceId(workspaceId).map { it.toCommonEntity().toModel(0) }

    override suspend fun getLastUpdated(): List<Folder> =
        delegate.getLastUpdated().map { it.toCommonEntity().toModel(0) }

    override suspend fun getFolderByParentId(id: String, workspaceId: String): List<Folder> =
        delegate.getFolderByParentId(id, workspaceId).map { it.toCommonEntity().toModel(0) }

    override fun listenForFolderByParentId(id: String, workspaceId: String): Flow<List<Folder>> =
        delegate.listenForFolderByParentId(id, workspaceId).map { list ->
            list.map { it.toCommonEntity().toModel(0) }
        }

    override suspend fun deleteById(id: String, workspaceId: String, lastUpdatedAt: Long): Int =
        delegate.deleteById(id, workspaceId, lastUpdatedAt)

    override suspend fun deleteByParentId(id: String, workspaceId: String, lastUpdatedAt: Long): Int =
        delegate.deleteByParentId(id, workspaceId, lastUpdatedAt)

    override suspend fun hardDeleteById(id: String, workspaceId: String): Int =
        delegate.hardDeleteById(id, workspaceId)

    override suspend fun getSoftDeletedByWorkspace(workspaceId: String): List<Folder> =
        delegate.getSoftDeletedByWorkspace(workspaceId).map { it.toCommonEntity().toModel(0) }

    override suspend fun getContentSyncedAt(folderId: String, workspaceId: String): Long? =
        delegate.getContentSyncedAt(folderId, workspaceId)

    override suspend fun setContentSyncedAt(folderId: String, workspaceId: String, contentSyncedAt: Long) {
        delegate.upsertContentSync(FolderContentSyncEntity(folderId, workspaceId, contentSyncedAt))
    }
}
