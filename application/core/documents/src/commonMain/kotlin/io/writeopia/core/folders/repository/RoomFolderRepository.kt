package io.writeopia.core.folders.repository

import io.writeopia.common.utils.persistence.daos.FolderCommonDao
import io.writeopia.models.Folder
import io.writeopia.models.search.FolderSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class RoomFolderRepository(
    private val folderRoomDao: FolderCommonDao
) : FolderRepository, FolderSearch {

    override suspend fun createFolder(folder: Folder) {
        updateFolder(folder)
    }

    override suspend fun updateFolder(folder: Folder) {
        folderRoomDao.upsertFolder(folder)
    }

    override suspend fun setLastUpdated(folderId: String, long: Long) {
        updateFolderById(folderId) { folder ->
            folder.copy(lastUpdatedAt = Instant.fromEpochMilliseconds(long))
        }
    }

    override suspend fun deleteFolderById(folderId: String) {
        folderRoomDao.deleteById(folderId)
    }

    override suspend fun deleteFolderByParent(folderId: String) {
        folderRoomDao.getFolderByParentId(folderId)
    }

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {
        ids.forEach { folderId ->
            updateFolderById(folderId) { folder ->
                folder.copy(favorite = true)
            }
        }
    }

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {
        ids.forEach { folderId ->
            updateFolderById(folderId) { folder ->
                folder.copy(favorite = false)
            }
        }
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun refreshFolders() {}

    override suspend fun getFolderById(id: String): Folder? =
        folderRoomDao.getFolderById(id)

    override suspend fun getFolderByParentId(parentId: String): List<Folder> =
        folderRoomDao.getFolderByParentId(parentId)

    override suspend fun listenForFoldersByParentId(
        parentId: String
    ): Flow<Map<String, List<Folder>>> {
        SelectedIds.ids.add(parentId)

        val flows = SelectedIds.ids.map {
            folderRoomDao.listenForFolderByParentId(parentId)
        }

        return if (flows.isNotEmpty()) {
            combine(flows) { arrayOfFolders -> arrayOfFolders.toList().flatten() }
                .map { folders ->
                    folders.groupBy { folder -> folder.parentId }
                }
        } else {
            flow { emit(emptyMap()) }
        }
    }

    override suspend fun stopListeningForFoldersByParentId(parentId: String) {
        SelectedIds.ids.remove(parentId)
    }

    override suspend fun search(query: String): List<Folder> =
        folderRoomDao.search(query)

    override suspend fun getLastUpdated(): List<Folder> =
        folderRoomDao.getLastUpdated()

    private suspend fun updateFolderById(id: String, func: (Folder) -> Folder) {
        folderRoomDao.getFolderById(id = id)
            ?.let(func)
            ?.let { folder ->
                updateFolder(folder)
            }
    }
}

private object SelectedIds {
    val ids = mutableSetOf<String>()
}
