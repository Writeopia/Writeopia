package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.FolderCommonDao
import io.writeopia.common.utils.persistence.folder.toModel
import io.writeopia.common.utils.persistence.folder.toRoomEntity
import io.writeopia.sdk.models.document.Folder
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

    override suspend fun getLastUpdated(): List<Folder> =
        delegate.getLastUpdated().map { it.toCommonEntity().toModel(0) }

    override suspend fun getFolderByParentId(id: String): List<Folder> =
        delegate.getFolderByParentId(id).map { it.toCommonEntity().toModel(0) }

    override fun listenForFolderByParentId(id: String): Flow<List<Folder>> =
        delegate.listenForFolderByParentId(id).map { list ->
            list.map { it.toCommonEntity().toModel(0) }
        }

    override suspend fun deleteById(id: String, lastUpdatedAt: Long): Int =
        delegate.deleteById(id, lastUpdatedAt)

    override suspend fun deleteByParentId(id: String, lastUpdatedAt: Long): Int =
        delegate.deleteByParentId(id, lastUpdatedAt)

    override suspend fun hardDeleteById(id: String): Int = delegate.hardDeleteById(id)

    override suspend fun getSoftDeletedByWorkspace(workspaceId: String): List<Folder> =
        delegate.getSoftDeletedByWorkspace(workspaceId).map { it.toCommonEntity().toModel(0) }
}
