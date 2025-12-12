@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.writeopia.core.folders.extensions.toEntity
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sqldelight.dao.FolderSqlDelightDao
import io.writeopia.sqldelight.extensions.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FolderRepositorySqlDelight(
    private val folderDao: FolderSqlDelightDao
) : FolderRepository {

    override suspend fun getFolderById(id: String): Folder? =
        folderDao.getFolderById(id)?.toModel(0)

    override suspend fun getFoldersForWorkspaceAfterTime(
        workspaceId: String,
        instant: Instant
    ): List<Folder> {
        return folderDao.selectByUserIdAfterTime(
            workspaceId,
            instant.toEpochMilliseconds()
        )
    }

    override suspend fun getFoldersForWorkspace(workspaceId: String): List<Folder> =
        folderDao.selectByWorkspaceId(workspaceId)

    override suspend fun createFolder(folder: Folder) {
        folderDao.createFolder(folder.toEntity())
    }

    override suspend fun updateFolder(folder: Folder) {
        folderDao.updateFolder(folder.toEntity())
    }

    override suspend fun setLastUpdated(folderId: String, long: Long) {
        folderDao.setLastUpdate(folderId, long)
    }

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {
        ids.forEach { id ->
            folderDao.favoriteById(id)
        }

        refreshFolders()
    }

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {
        ids.forEach { id ->
            folderDao.unFavoriteById(id)
        }

        refreshFolders()
    }

    override suspend fun getFolderByParentId(parentId: String, workspaceId: String): List<Folder> =
        folderDao.getFoldersByParentId(parentId, workspaceId)
            .map { (folderEntity, count) -> folderEntity.toModel(count) }

    override suspend fun listenForFoldersByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Folder>>> {
        return folderDao.listenForFolderByParentId(parentId, workspaceId)
            .map { folderEntityMap ->
                folderEntityMap.mapValues { (_, folderEntityListWithCount) ->
                    folderEntityListWithCount.map { (folderEntity, count) ->
                        folderEntity.toModel(count)
                    }
                }
            }
    }

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {
        folderDao.removeListening(parentId, workspaceId)
    }

    override suspend fun localOutDatedFolders(workspaceId: String): List<Folder> =
        getFoldersForWorkspace(workspaceId)

    override suspend fun deleteFolderById(folderId: String) {
        folderDao.deleteFolder(folderId)
    }

    override suspend fun deleteFolderByParent(folderId: String) {
        folderDao.deleteFolderByParent(folderId)
    }

    override suspend fun refreshFolders() {
        folderDao.refreshFolders()
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
        folderDao.moveToFolder(documentId = documentId, parentId = parentId)
        refreshFolders()
    }
}
