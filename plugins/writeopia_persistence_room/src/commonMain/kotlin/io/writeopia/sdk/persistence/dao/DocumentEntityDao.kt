package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.writeopia.sdk.models.CREATED_AT
import io.writeopia.sdk.models.DOCUMENT_ENTITY
import io.writeopia.sdk.models.LAST_UPDATED_AT
import io.writeopia.sdk.models.TITLE
import io.writeopia.sdk.persistence.entity.comment.COMMENT_ENTITY
import io.writeopia.sdk.persistence.entity.comment.CommentEntity
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

    @Query("DELETE FROM $DOCUMENT_ENTITY WHERE workspace_id = :userId")
    suspend fun purgeDocumentsByUserId(userId: String)

    @Query("SELECT * FROM $DOCUMENT_ENTITY ORDER BY $DOCUMENT_ENTITY.last_updated_at LIMIT 10")
    suspend fun selectByLastUpdated(): List<DocumentEntity>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE title LIKE '%' || :query || '%' ORDER BY last_updated_at")
    suspend fun search(query: String): List<DocumentEntity>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.id = :id")
    suspend fun loadDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.id in (:ids)")
    suspend fun loadDocumentByIds(ids: List<String>): List<DocumentEntity>

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "WHERE $DOCUMENT_ENTITY.id = :id AND workspace_id = :workspaceId"
    )
    suspend fun loadDocumentByIdForWorkspace(id: String, workspaceId: String): DocumentEntity?

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "WHERE $DOCUMENT_ENTITY.id in (:ids) AND workspace_id = :workspaceId"
    )
    suspend fun loadDocumentByIdsForWorkspace(
        ids: List<String>,
        workspaceId: String,
    ): List<DocumentEntity>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.parent_id = :id")
    suspend fun loadDocumentsByParentId(id: String): List<DocumentEntity>

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "WHERE $DOCUMENT_ENTITY.parent_id = :id AND workspace_id = :workspaceId"
    )
    suspend fun loadDocumentsByParentIdForWorkspace(
        id: String,
        workspaceId: String,
    ): List<DocumentEntity>

    @Query(
        "SELECT * " +
            "FROM $DOCUMENT_ENTITY " +
            "LEFT OUTER JOIN $STORY_UNIT_ENTITY " +
            "ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.parent_id = :folderId " +
            "AND $DOCUMENT_ENTITY.workspace_id = :workspaceId AND is_deleted = FALSE " +
            "AND ($DOCUMENT_ENTITY.last_updated_at > $DOCUMENT_ENTITY.last_synced_at " +
            "OR $DOCUMENT_ENTITY.last_synced_at IS NULL) " +
            "ORDER BY $DOCUMENT_ENTITY.created_at, $STORY_UNIT_ENTITY.position"
    )
    suspend fun loadOutdatedDocumentsByFolderId(
        folderId: String,
        workspaceId: String,
    ): Map<DocumentEntity, List<StoryStepEntity>>

    @Query("SELECT * FROM $DOCUMENT_ENTITY")
    suspend fun loadAllDocuments(): List<DocumentEntity>

    @Query("SELECT id FROM $DOCUMENT_ENTITY")
    suspend fun loadAllIds(): List<String>

    // The order here doesn't matter, because only one document should be returned
    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.id = :documentId AND is_deleted = FALSE " +
            "ORDER BY $DOCUMENT_ENTITY.created_at, $STORY_UNIT_ENTITY.position"
    )
    suspend fun loadDocumentWithContentById(
        documentId: String
    ): Map<DocumentEntity, List<StoryStepEntity>>

    // The order here doesn't matter, because only one document should be returned
    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.id IN (:documentIds) AND is_deleted = FALSE " +
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
            "JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.id IN (:documentIds) " +
            "AND $DOCUMENT_ENTITY.workspace_id = :workspaceId AND is_deleted = FALSE " +
            "ORDER BY " +
            "CASE WHEN :orderBy = '$TITLE' THEN $DOCUMENT_ENTITY.title END COLLATE NOCASE ASC, " +
            "CASE WHEN :orderBy = '$CREATED_AT' THEN $DOCUMENT_ENTITY.created_at END DESC, " +
            "CASE WHEN :orderBy = '$LAST_UPDATED_AT' THEN $DOCUMENT_ENTITY.last_updated_at END DESC, " +
            "$STORY_UNIT_ENTITY.position"
    )
    suspend fun loadDocumentWithContentByIdsForWorkspace(
        documentIds: List<String>,
        orderBy: String,
        workspaceId: String,
    ): Map<DocumentEntity, List<StoryStepEntity>>

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE workspace_id = :userId " +
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
            "JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE workspace_id = :userId AND is_deleted = FALSE " +
            "ORDER BY " +
//                "CASE WHEN :orderBy = \'$TITLE\' THEN $DOCUMENT_ENTITY.title END COLLATE NOCASE ASC, " +
//                "CASE WHEN :orderBy = \'$CREATED_AT\' THEN $DOCUMENT_ENTITY.created_at END DESC, " +
//                "CASE WHEN :orderBy = \'$LAST_UPDATED_AT\' THEN $DOCUMENT_ENTITY.last_updated_at END DESC, " +
            "$STORY_UNIT_ENTITY.position"
    )
    fun listenForDocumentsWithContentForWorkspace(
        userId: String
    ): Flow<Map<DocumentEntity, List<StoryStepEntity>>>

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.parent_id = :parentId AND is_deleted = FALSE " +
            "ORDER BY " +
//                "CASE WHEN :orderBy = \'$TITLE\' THEN $DOCUMENT_ENTITY.title END COLLATE NOCASE ASC, " +
//                "CASE WHEN :orderBy = \'$CREATED_AT\' THEN $DOCUMENT_ENTITY.created_at END DESC, " +
//                "CASE WHEN :orderBy = \'$LAST_UPDATED_AT\' THEN $DOCUMENT_ENTITY.last_updated_at END DESC, " +
            "$STORY_UNIT_ENTITY.position"
    )
    fun listenForDocumentsWithContentByParentId(
        parentId: String
    ): Flow<Map<DocumentEntity, List<StoryStepEntity>>>

    @Query(
        "SELECT * FROM $DOCUMENT_ENTITY " +
            "JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.parent_id = :parentId " +
            "AND $DOCUMENT_ENTITY.workspace_id = :workspaceId AND is_deleted = FALSE " +
            "ORDER BY $STORY_UNIT_ENTITY.position"
    )
    fun listenForDocumentsWithContentByParentIdForWorkspace(
        parentId: String,
        workspaceId: String,
    ): Flow<Map<DocumentEntity, List<StoryStepEntity>>>

    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.id = :id")
    fun listenForDocumentById(id: String): Flow<DocumentEntity?>

    @Query("UPDATE $DOCUMENT_ENTITY set workspace_id = :newUserId WHERE workspace_id = :oldUserId")
    suspend fun moveDocumentsToNewUser(oldUserId: String, newUserId: String)

    @Query("SELECT title FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.id = :documentId")
    suspend fun getDocumentTitleById(documentId: String): String?

    @Query(
        "SELECT * " +
            "FROM $DOCUMENT_ENTITY " +
            "JOIN $STORY_UNIT_ENTITY ON $DOCUMENT_ENTITY.id = $STORY_UNIT_ENTITY.document_id " +
            "WHERE $DOCUMENT_ENTITY.workspace_id = :workspaceId AND is_deleted = FALSE " +
            "ORDER BY " +
            "$STORY_UNIT_ENTITY.position"
    )
    suspend fun loadOutdatedDocumentsWithContentForWorkspace(
        workspaceId: String
    ): Map<DocumentEntity, List<StoryStepEntity>>

    // Hard delete: permanently removes documents from database (scoped to workspace)
    @Query("DELETE FROM $DOCUMENT_ENTITY WHERE $DOCUMENT_ENTITY.id in (:ids) AND workspace_id = :workspaceId")
    suspend fun hardDeleteDocumentByIds(ids: List<String>, workspaceId: String)

    // Hard delete story steps by document IDs (used internally for atomic deletion)
    @Query("DELETE FROM $STORY_UNIT_ENTITY WHERE $STORY_UNIT_ENTITY.document_id IN (:documentIds)")
    suspend fun hardDeleteStoryStepsByDocumentIds(documentIds: List<String>)

    @Query("DELETE FROM $COMMENT_ENTITY WHERE document_id IN (:documentIds)")
    suspend fun hardDeleteCommentsByDocumentIds(documentIds: List<String>)

    @Query(
        "SELECT id FROM $DOCUMENT_ENTITY " +
            "WHERE id IN (:ids) AND workspace_id = :workspaceId"
    )
    suspend fun loadOwnedDocumentIds(ids: List<String>, workspaceId: String): List<String>

    @Query("SELECT id FROM $DOCUMENT_ENTITY WHERE workspace_id = :workspaceId")
    suspend fun loadDocumentIdsByWorkspace(workspaceId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoryStepsForDocument(vararg storySteps: StoryStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommentsForDocument(vararg comments: CommentEntity)

    @Transaction
    suspend fun saveDocumentWithContent(
        document: DocumentEntity,
        storySteps: List<StoryStepEntity>,
        comments: List<CommentEntity>,
    ) {
        insertDocuments(document)
        hardDeleteStoryStepsByDocumentIds(listOf(document.id))
        if (storySteps.isNotEmpty()) {
            insertStoryStepsForDocument(*storySteps.toTypedArray())
        }

        hardDeleteCommentsByDocumentIds(listOf(document.id))
        if (comments.isNotEmpty()) {
            insertCommentsForDocument(*comments.toTypedArray())
        }
    }

    @Transaction
    suspend fun purgeDocumentsWithContentByWorkspace(workspaceId: String) {
        val documentIds = loadDocumentIdsByWorkspace(workspaceId)
        if (documentIds.isNotEmpty()) {
            hardDeleteCommentsByDocumentIds(documentIds)
            hardDeleteStoryStepsByDocumentIds(documentIds)
        }
        purgeDocumentsByUserId(workspaceId)
    }

    // Atomic hard delete: removes only documents owned by the requested workspace.
    @Transaction
    suspend fun hardDeleteDocumentsWithContentByIds(ids: List<String>, workspaceId: String) {
        val ownedIds = loadOwnedDocumentIds(ids, workspaceId)
        if (ownedIds.isEmpty()) return

        hardDeleteCommentsByDocumentIds(ownedIds)
        hardDeleteStoryStepsByDocumentIds(ownedIds)
        hardDeleteDocumentByIds(ownedIds, workspaceId)
    }

    // Get soft-deleted documents for a workspace (for syncing deletions to backend)
    @Query("SELECT * FROM $DOCUMENT_ENTITY WHERE workspace_id = :workspaceId AND is_deleted = TRUE")
    suspend fun getSoftDeletedByWorkspace(workspaceId: String): List<DocumentEntity>
}
