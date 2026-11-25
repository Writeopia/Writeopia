package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.writeopia.sdk.persistence.entity.document.PENDING_FAVORITE_ENTITY
import io.writeopia.sdk.persistence.entity.document.PendingFavoriteEntity

@Dao
interface PendingFavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(changes: List<PendingFavoriteEntity>)

    @Query("SELECT * FROM $PENDING_FAVORITE_ENTITY WHERE workspace_id = :workspaceId ORDER BY changed_at")
    suspend fun loadByWorkspaceId(workspaceId: String): List<PendingFavoriteEntity>

    /** Forgets the record of the document unless it changed again after [changedAt]. */
    @Query("DELETE FROM $PENDING_FAVORITE_ENTITY WHERE document_id = :documentId AND changed_at <= :changedAt")
    suspend fun deleteUpTo(documentId: String, changedAt: Long)
}
