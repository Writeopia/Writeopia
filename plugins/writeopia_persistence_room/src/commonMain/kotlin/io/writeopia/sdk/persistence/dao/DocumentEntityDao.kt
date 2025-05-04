package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.writeopia.sdk.models.CREATED_AT
import io.writeopia.sdk.models.DOCUMENT_ENTITY
import io.writeopia.sdk.models.LAST_UPDATED_AT
import io.writeopia.sdk.models.TITLE
import io.writeopia.sdk.persistence.entity.document.DocumentEntity
import io.writeopia.sdk.persistence.entity.story.STORY_UNIT_ENTITY
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentEntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(vararg documents: DocumentEntity)

    @Update
    suspend fun updateDocument(vararg documents: DocumentEntity)

    @Delete
    suspend fun deleteDocuments(vararg documents: DocumentEntity)

    @Query("DELETE FROM $DOCUMENT_ENTITY WHERE user_id = :userId")
    suspend fun deleteDocumentsByUserId(userId: String)

    @Query("SELECT * FROM $DOCUMENT_ENTITY ORDER BY $DOCUMENT_ENTITY.last_updated_at LIMIT 10")
    suspend fun selectByLastUpdated(): List<DocumentEntity>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE title LIKE '%' || :query || '%' ORDER BY last_updated_at")
    suspend fun search(query: String): List<DocumentEntity>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.id = :id")
    suspend fun loadDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.id in (:ids)")
    suspend fun loadDocumentByIds(ids: List<String>): List<DocumentEntity>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.parent_id = :id")
    suspend fun loadDocumentsByParentId(id: String): List<DocumentEntity>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.parent_id = :folderId AND $DOCUMENT_ENTITY.last_updated_at >  $DOCUMENT_ENTITY.last_synced_at")
    suspend fun loadOutdatedDocumentsByFolderId(folderId: String): List<DocumentEntity>

    @Query("SELECT * FROM $DOCUMENT_ENTITY")
    suspend fun loadAllDocuments(): List<DocumentEntity>

    @Query("SELECT id FROM $DOCUMENT_ENTITY")
    suspend fun loadAllIds(): List<String>

    // The order here doesn't matter, because only one document should be returned
    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "LEFT JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.id = :documentId " +
            "ORDER BY $DOCUMENT_ENTITY.created_at, $STORY_UNIT_ENTITY.position"
    )
    suspend fun loadDocumentWithContentById(
        documentId: String
    ): Map<DocumentEntity, List<StoryStepEntity>>

    // The order here doesn't matter, because only one document should be returned
    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "LEFT JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.id IN (:documentIds) " +
            "ORDER BY " +
            "CASE WHEN :orderBy = \'$TITLE\' THEN $DOCUMENT_ENTITY.title END COLLATE NOCASE ASC, " +
            "CASE WHEN :orderBy = \'$CREATED_AT\' THEN $DOCUMENT_ENTITY.created_at END DESC, " +
            "CASE WHEN :orderBy = \'$LAST_UPDATED_AT\' THEN $DOCUMENT_ENTITY.last_updated_at END DESC, " +
            "$STORY_UNIT_ENTITY.position"
    )
    suspend fun loadDocumentWithContentByIds(
        documentIds: List<String>,
        orderBy: String
    ): Map<DocumentEntity, List<StoryStepEntity>>

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "LEFT JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE user_id = :userId " +
            "ORDER BY " +
//                "CASE WHEN :orderBy = \'$TITLE\' THEN $DOCUMENT_ENTITY.title END COLLATE NOCASE ASC, " +
//                "CASE WHEN :orderBy = \'$CREATED_AT\' THEN $DOCUMENT_ENTITY.created_at END DESC, " +
//                "CASE WHEN :orderBy = \'$LAST_UPDATED_AT\' THEN $DOCUMENT_ENTITY.last_updated_at END DESC, " +
            "$STORY_UNIT_ENTITY.position"
    )
    suspend fun loadDocumentsWithContentForUser(
        userId: String
    ): Map<DocumentEntity, List<StoryStepEntity>>

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "LEFT JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE user_id = :userId " +
            "ORDER BY " +
//                "CASE WHEN :orderBy = \'$TITLE\' THEN $DOCUMENT_ENTITY.title END COLLATE NOCASE ASC, " +
//                "CASE WHEN :orderBy = \'$CREATED_AT\' THEN $DOCUMENT_ENTITY.created_at END DESC, " +
//                "CASE WHEN :orderBy = \'$LAST_UPDATED_AT\' THEN $DOCUMENT_ENTITY.last_updated_at END DESC, " +
            "$STORY_UNIT_ENTITY.position"
    )
    fun listenForDocumentsWithContentForUser(
        userId: String
    ): Flow<Map<DocumentEntity, List<StoryStepEntity>>>

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "LEFT JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.parent_id = :parentId " +
            "ORDER BY " +
//                "CASE WHEN :orderBy = \'$TITLE\' THEN $DOCUMENT_ENTITY.title END COLLATE NOCASE ASC, " +
//                "CASE WHEN :orderBy = \'$CREATED_AT\' THEN $DOCUMENT_ENTITY.created_at END DESC, " +
//                "CASE WHEN :orderBy = \'$LAST_UPDATED_AT\' THEN $DOCUMENT_ENTITY.last_updated_at END DESC, " +
            "$STORY_UNIT_ENTITY.position"
    )
    fun listenForDocumentsWithContentByParentId(
        parentId: String
    ): Flow<Map<DocumentEntity, List<StoryStepEntity>>>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.id = :id")
    fun listenForDocumentById(id: String): Flow<DocumentEntity?>

    @Query("UPDATE $DOCUMENT_ENTITY set user_id = :newUserId WHERE user_id = :oldUserId")
    suspend fun moveDocumentsToNewUser(oldUserId: String, newUserId: String)
}
