package io.writeopia.persistence.room.data.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.writeopia.persistence.room.data.entities.FOLDER_ENTITY
import io.writeopia.persistence.room.data.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderRoomDao {

    @Upsert
    suspend fun upsertFolder(folderEntity: FolderEntity)

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE folder_id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("SELECT * " +
        "FROM $FOLDER_ENTITY " +
        "WHERE title LIKE '%' || :query || '%' " +
        "AND workspace_id = :workspaceId AND folder_id != 'root' AND deleted = 0 " +
        "ORDER BY last_updated_at")
    suspend fun search(query: String, workspaceId: String): List<FolderEntity>

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE deleted = 0 ORDER BY last_updated_at LIMIT 15")
    suspend fun getLastUpdated(): List<FolderEntity>

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE workspace_id = :workspaceId AND deleted = 0")
    fun getFoldersByWorkspaceId(workspaceId: String): List<FolderEntity>

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE parent_id = :id AND deleted = 0")
    suspend fun getFolderByParentId(id: String): List<FolderEntity>

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE parent_id = :id AND deleted = 0")
    fun listenForFolderByParentId(id: String): Flow<List<FolderEntity>>

    // Soft delete: marks folder as deleted (scoped to workspace)
    @Query("UPDATE $FOLDER_ENTITY SET deleted = 1, last_updated_at = :lastUpdatedAt WHERE folder_id = :id AND workspace_id = :workspaceId")
    suspend fun softDeleteById(id: String, workspaceId: String, lastUpdatedAt: Long): Int

    // Hard delete: permanently removes folder (use after backend confirms deletion, scoped to workspace)
    @Query("DELETE FROM $FOLDER_ENTITY WHERE folder_id = :id AND workspace_id = :workspaceId")
    suspend fun hardDeleteById(id: String, workspaceId: String): Int

    // Soft delete method (scoped to workspace)
    @Query("UPDATE $FOLDER_ENTITY SET deleted = 1, last_updated_at = :lastUpdatedAt WHERE folder_id = :id AND workspace_id = :workspaceId")
    suspend fun deleteById(id: String, workspaceId: String, lastUpdatedAt: Long): Int

    @Query("UPDATE $FOLDER_ENTITY SET deleted = 1, last_updated_at = :lastUpdatedAt WHERE parent_id = :id AND workspace_id = :workspaceId")
    suspend fun deleteByParentId(id: String, workspaceId: String, lastUpdatedAt: Long): Int

    // Get soft-deleted folders for a workspace (for syncing deletions to backend)
    @Query("SELECT * FROM $FOLDER_ENTITY WHERE workspace_id = :workspaceId AND deleted = 1")
    suspend fun getSoftDeletedByWorkspace(workspaceId: String): List<FolderEntity>
}
