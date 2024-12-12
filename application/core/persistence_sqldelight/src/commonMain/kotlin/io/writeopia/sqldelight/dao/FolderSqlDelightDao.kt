package io.writeopia.sqldelight.dao

import io.writeopia.app.sql.FolderEntity
import io.writeopia.app.sql.FolderEntityQueries
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.utils.sumValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class FolderSqlDelightDao(database: WriteopiaDb?) {

    private val _foldersStateFlow =
        MutableStateFlow<Map<String, List<Pair<FolderEntity, Long>>>>(emptyMap())

    private val folderEntityQueries: FolderEntityQueries? = database?.folderEntityQueries

    suspend fun getFolderById(id: String) : FolderEntity? =
        folderEntityQueries?.selectFolderById(id)?.executeAsOneOrNull()

    suspend fun createFolder(folder: FolderEntity) {
        folderEntityQueries?.insert(
            id = folder.id,
            parent_id = folder.parent_id,
            user_id = folder.user_id,
            title = folder.title,
            created_at = folder.created_at,
            last_updated_at = folder.last_updated_at,
            favorite = folder.favorite,
        )
        refreshFolders()
    }

    fun search(query: String) = folderEntityQueries?.query(query)?.executeAsList() ?: emptyList()

    fun getLastUpdated() = folderEntityQueries?.getLastUpdated()?.executeAsList() ?: emptyList()

    suspend fun updateFolder(folder: FolderEntity) {
        folderEntityQueries?.insert(
            id = folder.id,
            parent_id = folder.parent_id,
            user_id = folder.user_id,
            title = folder.title,
            created_at = folder.created_at,
            last_updated_at = folder.last_updated_at,
            favorite = folder.favorite,
        )
        refreshFolders()
    }

    suspend fun setLastUpdate(id: String, lastUpdateTimeStamp: Long) {
        folderEntityQueries?.setLastUpdate(lastUpdateTimeStamp, id)
    }

    suspend fun favoriteById(id: String) {
        folderEntityQueries?.favoriteById(1, id)
    }

    suspend fun unFavoriteById(id: String) {
        folderEntityQueries?.favoriteById(0, id)
    }

    fun listenForFolderByParentId(
        parentId: String,
        coroutineScope: CoroutineScope?
    ): Flow<Map<String, List<Pair<FolderEntity, Long>>>> {
        coroutineScope?.launch {
            SelectedIds.ids.add(parentId)
            refreshFolders()
        }

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

    suspend fun getFoldersByParentId(parentId: String): List<Pair<FolderEntity, Long>> {
        val countMap = countAllItems()

        return folderEntityQueries?.selectChildrenFolder(parent_id = parentId)
            ?.executeAsList()
            ?.map { folderEntity ->
                folderEntity to (countMap[folderEntity.id] ?: 0)
            } ?: emptyList()
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
        _foldersStateFlow.value = SelectedIds.ids.associateWith {
            getFoldersByParentId(it)
        }
    }

    suspend fun removeListening(id: String) {
        SelectedIds.ids.remove(id)
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
