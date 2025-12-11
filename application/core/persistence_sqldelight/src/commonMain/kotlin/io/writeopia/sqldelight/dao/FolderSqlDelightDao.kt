@file:OptIn(ExperimentalTime::class)

package io.writeopia.sqldelight.dao

import io.writeopia.app.sql.FolderEntity
import io.writeopia.app.sql.FolderEntityQueries
import io.writeopia.sdk.models.document.Folder
import io.writeopia.models.interfaces.search.FolderSearch
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.extensions.toModel
import io.writeopia.sqldelight.utils.sumValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FolderSqlDelightDao(database: WriteopiaDb?) : FolderSearch {

    private val _foldersStateFlow =
        MutableStateFlow<Map<String, List<Pair<FolderEntity, Long>>>>(emptyMap())

    private val folderEntityQueries: FolderEntityQueries? = database?.folderEntityQueries

    suspend fun getFolderById(id: String): FolderEntity? =
        folderEntityQueries?.selectFolderById(id)?.executeAsOneOrNull()

    suspend fun createFolder(folder: FolderEntity) {
        folderEntityQueries?.insert(
            id = folder.id,
            parent_id = folder.parent_id,
            workspace_id = folder.workspace_id,
            title = folder.title,
            created_at = folder.created_at,
            last_updated_at = folder.last_updated_at,
            favorite = folder.favorite,
            icon = folder.icon,
            icon_tint = folder.icon_tint,
            last_synced_at = folder.last_synced_at
        )
        refreshFolders()
    }

    override suspend fun search(query: String, workspaceId: String): List<Folder> =
        folderEntityQueries?.query(query, workspace_id = workspaceId)
            ?.executeAsList()
            ?.map { folderEntity -> folderEntity.toModel(0) }
            ?: emptyList()

    override suspend fun getLastUpdated(): List<Folder> = folderEntityQueries?.getLastUpdated()
        ?.executeAsList()
        ?.map { folderEntity -> folderEntity.toModel(0) }
        ?: emptyList()

    suspend fun updateFolder(folder: FolderEntity) {
        folderEntityQueries?.insert(
            id = folder.id,
            parent_id = folder.parent_id,
            workspace_id = folder.workspace_id,
            title = folder.title,
            created_at = folder.created_at,
            last_updated_at = folder.last_updated_at,
            favorite = folder.favorite,
            icon = folder.icon,
            icon_tint = folder.icon_tint,
            last_synced_at = folder.last_synced_at
        )
        refreshFolders()
    }

    suspend fun selectByWorkspaceId(userId: String): List<Folder> =
        folderEntityQueries?.selectByWorkspace(userId)
            ?.executeAsList()
            ?.map { it.toModel(0) }
            ?: emptyList()

    suspend fun selectByUserIdAfterTime(userId: String, instant: Long): List<Folder> =
        folderEntityQueries?.selectByWorkspaceAfterTime(userId, instant)
            ?.executeAsList()
            ?.map { it.toModel(0) }
            ?: emptyList()

    suspend fun setLastUpdate(id: String, lastUpdateTimeStamp: Long) {
        folderEntityQueries?.setLastUpdate(lastUpdateTimeStamp, id)
    }

    suspend fun favoriteById(id: String) {
        folderEntityQueries?.favoriteById(1, id)
    }

    suspend fun unFavoriteById(id: String) {
        folderEntityQueries?.favoriteById(0, id)
    }

    suspend fun listenForFolderByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Pair<FolderEntity, Long>>>> {
        SelectedIds.ids.add("$parentId:$workspaceId")
        refreshFolders()

        return _foldersStateFlow
    }

    suspend fun deleteFolder(folderId: String) {
        folderEntityQueries?.deleteFolder(folderId)
        refreshFolders()
    }

    suspend fun deleteFolderByParent(folderId: String) {
        folderEntityQueries?.deleteFolderByParent(folderId)
        refreshFolders()
    }

    suspend fun getFoldersByParentId(
        parentId: String,
        workspaceId: String
    ): List<Pair<FolderEntity, Long>> {
        val countMap = countAllItems()

        val result = folderEntityQueries?.selectChildrenFolder(
            parent_id = parentId,
            workspace_id = workspaceId
        )
            ?.executeAsList()
            ?.map { folderEntity ->
                folderEntity to (countMap[folderEntity.id] ?: 0)
            } ?: emptyList()

        return result
    }

    private fun countAllItems(): Map<String, Long> {
        val foldersCount = folderEntityQueries?.countAllFolderItems()
            ?.executeAsList()
            ?.associate { countByParent ->
                countByParent.parent_id to countByParent.COUNT
            } ?: emptyMap()

        val documentsCount = folderEntityQueries?.countAllDocumentItems()
            ?.executeAsList()
            ?.associate { countByParent ->
                countByParent.parent_document_id to countByParent.COUNT
            } ?: emptyMap()

        return foldersCount.sumValues(documentsCount)
    }

    suspend fun refreshFolders() {
        _foldersStateFlow.value = SelectedIds.ids.associateWith { key ->
            val (parentId, workspaceId) = key.split(":", limit = 2)
            getFoldersByParentId(parentId, workspaceId)
        }
    }

    suspend fun removeListening(parentId: String, workspaceId: String) {
        SelectedIds.ids.remove("$parentId:$workspaceId")
        refreshFolders()
    }

    suspend fun moveToFolder(documentId: String, parentId: String) {
        folderEntityQueries?.moveToFolder(
            parentId,
            Clock.System.now().toEpochMilliseconds(),
            documentId
        )
    }
}

private object SelectedIds {
    val ids = mutableSetOf<String>()
}

