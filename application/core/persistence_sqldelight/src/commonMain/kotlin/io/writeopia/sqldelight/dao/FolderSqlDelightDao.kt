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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FolderSqlDelightDao(database: WriteopiaDb?) : FolderSearch {

    private val _foldersStateFlow =
        MutableStateFlow<Map<String, List<Pair<FolderEntity, Long>>>>(emptyMap())

    // Bumped on every refresh: the favorites are read again on each change, since they can be
    // anywhere in the workspace and not only under the listened parents.
    private val _refreshCount = MutableStateFlow(0)

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
            last_synced_at = folder.last_synced_at,
            deleted = folder.deleted
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
            last_synced_at = folder.last_synced_at,
            deleted = folder.deleted
        )
        refreshFolders()
    }

    suspend fun selectByWorkspaceId(userId: String): List<Folder> =
        folderEntityQueries?.selectByWorkspace(userId)
            ?.executeAsList()
            ?.map { it.toModel(0) }
            ?: emptyList()

    /** Folders changed locally since they were last sent to the backend, or never sent. */
    suspend fun selectOutdatedByWorkspaceId(workspaceId: String): List<Folder> =
        folderEntityQueries?.selectOutdatedByWorkspace(workspaceId)
            ?.executeAsList()
            ?.map { it.toModel(0) }
            ?: emptyList()

    suspend fun selectByUserIdAfterTime(userId: String, instant: Long): List<Folder> =
        folderEntityQueries?.selectByWorkspaceAfterTime(userId, instant)
            ?.executeAsList()
            ?.map { it.toModel(0) }
            ?: emptyList()

    suspend fun getContentSyncedAt(folderId: String, workspaceId: String): Long? =
        folderEntityQueries?.selectContentSyncedAt(folderId, workspaceId)?.executeAsOneOrNull()

    suspend fun setContentSyncedAt(folderId: String, workspaceId: String, contentSyncedAt: Long) {
        folderEntityQueries?.upsertContentSyncedAt(folderId, workspaceId, contentSyncedAt)
    }

    suspend fun setLastUpdate(id: String, lastUpdateTimeStamp: Long) {
        folderEntityQueries?.setLastUpdate(lastUpdateTimeStamp, id)
    }

    suspend fun favoriteById(id: String) {
        folderEntityQueries?.favoriteById(1, Clock.System.now().toEpochMilliseconds(), id)
    }

    suspend fun unFavoriteById(id: String) {
        folderEntityQueries?.favoriteById(0, Clock.System.now().toEpochMilliseconds(), id)
    }

    suspend fun listenForFolderByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Pair<FolderEntity, Long>>>> {
        SelectedIds.ids.add("$parentId:$workspaceId")
        refreshFolders()

        return _foldersStateFlow
    }

    /**
     * Soft delete: marks folder as deleted but keeps in database (scoped to workspace).
     * Use this for optimistic deletion - the folder can be synced to backend
     * and then hard deleted once confirmed.
     */
    suspend fun deleteFolder(folderId: String, workspaceId: String) {
        folderEntityQueries?.deleteFolder(Clock.System.now().toEpochMilliseconds(), folderId, workspaceId)
        refreshFolders()
    }

    /**
     * Soft delete by parent: marks all child folders as deleted (scoped to workspace).
     */
    suspend fun deleteFolderByParent(folderId: String, workspaceId: String) {
        folderEntityQueries?.deleteFolderByParent(
            Clock.System.now().toEpochMilliseconds(),
            folderId,
            workspaceId
        )
        refreshFolders()
    }

    /**
     * Hard delete: permanently removes folder from database (scoped to workspace).
     * Use this after backend has confirmed the deletion.
     */
    suspend fun hardDeleteFolder(folderId: String, workspaceId: String) {
        folderEntityQueries?.hardDeleteFolder(folderId, workspaceId)
        refreshFolders()
    }

    /**
     * Hard delete by parent: permanently removes all child folders from database (scoped to workspace).
     */
    suspend fun hardDeleteFolderByParent(folderId: String, workspaceId: String) {
        folderEntityQueries?.hardDeleteFolderByParent(folderId, workspaceId)
        refreshFolders()
    }

    /**
     * Get all soft-deleted folders for a workspace.
     * Use this to find folders that need to be synced to backend for deletion.
     */
    suspend fun getSoftDeletedFolders(workspaceId: String): List<Folder> =
        folderEntityQueries?.selectSoftDeletedByWorkspace(workspaceId)
            ?.executeAsList()
            ?.map { it.toModel(0) }
            ?: emptyList()

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
        _refreshCount.update { count -> count + 1 }
    }

    fun listenForFavoriteFolders(workspaceId: String): Flow<List<Pair<FolderEntity, Long>>> =
        _refreshCount.map { getFavoriteFolders(workspaceId) }

    private fun getFavoriteFolders(workspaceId: String): List<Pair<FolderEntity, Long>> {
        val countMap = countAllItems()

        return folderEntityQueries?.selectFavoritesByWorkspace(workspaceId)
            ?.executeAsList()
            ?.map { folderEntity -> folderEntity to (countMap[folderEntity.id] ?: 0) }
            ?: emptyList()
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

