@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface FolderRepository {

    suspend fun getFolderById(id: String): Folder?

    suspend fun getFolderByParentId(parentId: String, workspaceId: String): List<Folder>

    suspend fun getFoldersForWorkspaceAfterTime(workspaceId: String, instant: Instant): List<Folder>

    suspend fun getFoldersForWorkspace(workspaceId: String): List<Folder>

    suspend fun createFolder(folder: Folder)

    suspend fun updateFolder(folder: Folder)

    suspend fun setLastUpdated(folderId: String, long: Long)

    /**
     * Soft delete: marks folder as deleted but keeps in database (scoped to workspace).
     * The folder will be synced to backend and then hard deleted once confirmed.
     */
    suspend fun deleteFolderById(folderId: String, workspaceId: String)

    suspend fun deleteFolderByParent(folderId: String, workspaceId: String)

    /**
     * Hard delete: permanently removes folder from database (scoped to workspace).
     * Use this after backend has confirmed the deletion.
     */
    suspend fun hardDeleteFolderById(folderId: String, workspaceId: String)

    /**
     * Get all soft-deleted folders for a workspace.
     * Use this to find folders that need to be synced to backend for deletion.
     */
    suspend fun getSoftDeletedFolders(workspaceId: String): List<Folder>

    suspend fun favoriteDocumentByIds(ids: Set<String>)

    suspend fun unFavoriteDocumentByIds(ids: Set<String>)

    suspend fun moveToFolder(documentId: String, parentId: String)

    suspend fun refreshFolders()

    suspend fun listenForFoldersByParentId(parentId: String, workspaceId: String): Flow<Map<String, List<Folder>>>

    suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String)

    suspend fun localOutDatedFolders(workspaceId: String): List<Folder>
}
