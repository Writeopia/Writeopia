package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.writeopia.sdk.persistence.entity.comment.COMMENT_ENTITY
import io.writeopia.sdk.persistence.entity.comment.CommentEntity

@Dao
interface CommentEntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(vararg comments: CommentEntity)

    @Query(
        "SELECT * FROM $COMMENT_ENTITY WHERE document_id = :documentId " +
            "ORDER BY conversation_position, comment_position"
    )
    suspend fun loadByDocumentId(documentId: String): List<CommentEntity>

    @Query(
        "SELECT * FROM $COMMENT_ENTITY WHERE document_id IN (:documentIds) " +
            "ORDER BY document_id, conversation_position, comment_position"
    )
    suspend fun loadByDocumentIds(documentIds: List<String>): List<CommentEntity>

    @Query("DELETE FROM $COMMENT_ENTITY WHERE document_id = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("DELETE FROM $COMMENT_ENTITY WHERE document_id IN (:documentIds)")
    suspend fun deleteByDocumentIds(documentIds: List<String>)
}
