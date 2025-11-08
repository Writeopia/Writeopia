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

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE title LIKE '%' || :query || '%' AND workspace_id = :workspaceId ORDER BY last_updated_at")
    suspend fun search(query: String, workspaceId: String): List<FolderEntity>

    @Query("SELECT * FROM $FOLDER_ENTITY ORDER BY last_updated_at LIMIT 15")
    suspend fun getLastUpdated(): List<FolderEntity>

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE workspace_id = :workspaceId")
    fun getFoldersByWorkspaceId(workspaceId: String): List<FolderEntity>

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE parent_id = :id")
    suspend fun getFolderByParentId(id: String): List<FolderEntity>

    @Query("SELECT * FROM $FOLDER_ENTITY WHERE parent_id = :id")
    fun listenForFolderByParentId(id: String): Flow<List<FolderEntity>>

    @Query("DELETE FROM $FOLDER_ENTITY WHERE folder_id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM $FOLDER_ENTITY WHERE parent_id = :id")
    suspend fun deleteByParentId(id: String): Int
}
