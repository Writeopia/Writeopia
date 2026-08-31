package io.writeopia.common.utils.persistence.daos

import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.flow.Flow

interface FolderCommonDao {
    suspend fun upsertFolder(folderEntity: Folder)

    suspend fun getFolderById(id: String): Folder?

    suspend fun search(query: String, workspaceId: String): List<Folder>

    suspend fun getFoldersForWorkspace(workspaceId: String): List<Folder>

    suspend fun getLastUpdated(): List<Folder>

    suspend fun getFolderByParentId(id: String): List<Folder>

    fun listenForFolderByParentId(id: String): Flow<List<Folder>>

    /**
     * Soft delete: marks folder as deleted but keeps in database.
     */
    suspend fun deleteById(id: String, lastUpdatedAt: Long): Int

    suspend fun deleteByParentId(id: String, lastUpdatedAt: Long): Int

    /**
     * Hard delete: permanently removes folder from database.
     * Use this after backend has confirmed the deletion.
     */
    suspend fun hardDeleteById(id: String): Int

    /**
     * Get all soft-deleted folders for a workspace.
     * Use this to find folders that need to be synced to backend for deletion.
     */
    suspend fun getSoftDeletedByWorkspace(workspaceId: String): List<Folder>
}
