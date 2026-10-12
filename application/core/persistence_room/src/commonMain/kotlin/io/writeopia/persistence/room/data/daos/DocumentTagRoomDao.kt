package io.writeopia.persistence.room.data.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.writeopia.persistence.room.data.entities.DOCUMENT_TAG_ENTITY
import io.writeopia.persistence.room.data.entities.DocumentTagRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentTagRoomDao {

    @Upsert
    suspend fun upsertTag(tag: DocumentTagRoomEntity)

    @Query("SELECT * FROM $DOCUMENT_TAG_ENTITY WHERE id = :id")
    suspend fun getTagById(id: String): DocumentTagRoomEntity?

    @Query("SELECT * FROM $DOCUMENT_TAG_ENTITY WHERE workspace_id = :workspaceId AND deleted = 0 ORDER BY name")
    suspend fun getTagsByWorkspaceId(workspaceId: String): List<DocumentTagRoomEntity>

    @Query("SELECT * FROM $DOCUMENT_TAG_ENTITY WHERE workspace_id = :workspaceId ORDER BY name")
    suspend fun getAllTagsByWorkspaceId(workspaceId: String): List<DocumentTagRoomEntity>

    @Query("SELECT * FROM $DOCUMENT_TAG_ENTITY WHERE workspace_id = :workspaceId AND deleted = 0 ORDER BY name")
    fun listenForTagsByWorkspaceId(workspaceId: String): Flow<List<DocumentTagRoomEntity>>

    @Query(
        "UPDATE $DOCUMENT_TAG_ENTITY SET deleted = 1, last_updated_at = :lastUpdatedAt " +
            "WHERE id = :id AND workspace_id = :workspaceId"
    )
    suspend fun softDeleteById(id: String, workspaceId: String, lastUpdatedAt: Long)

    @Query("UPDATE $DOCUMENT_TAG_ENTITY SET workspace_id = :newWorkspaceId WHERE workspace_id = :oldWorkspaceId")
    suspend fun moveToWorkspace(oldWorkspaceId: String, newWorkspaceId: String)
}
