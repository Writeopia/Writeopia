package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.writeopia.sdk.models.CREATED_AT
import io.writeopia.sdk.models.LAST_UPDATED_AT
import io.writeopia.sdk.models.PDF_DOCUMENT_ENTITY
import io.writeopia.sdk.models.TITLE
import io.writeopia.sdk.persistence.entity.document.PdfDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentEntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(vararg documents: PdfDocumentEntity)

    @Update
    suspend fun updateDocument(vararg documents: PdfDocumentEntity)

    @Delete
    suspend fun deleteDocuments(vararg documents: PdfDocumentEntity)

    @Query("DELETE FROM $PDF_DOCUMENT_ENTITY WHERE workspace_id = :workspaceId")
    suspend fun purgeDocumentsByWorkspaceId(workspaceId: String)

    @Query("SELECT * FROM $PDF_DOCUMENT_ENTITY WHERE id = :id")
    suspend fun loadDocumentById(id: String): PdfDocumentEntity?

    @Query("SELECT * FROM $PDF_DOCUMENT_ENTITY WHERE id IN (:ids)")
    suspend fun loadDocumentByIds(ids: List<String>): List<PdfDocumentEntity>

    @Query("SELECT * FROM $PDF_DOCUMENT_ENTITY WHERE parent_id = :parentId AND is_deleted = FALSE " +
            "ORDER BY " +
            "CASE WHEN :orderBy = '$TITLE' THEN title END COLLATE NOCASE ASC, " +
            "CASE WHEN :orderBy = '$CREATED_AT' THEN created_at END DESC, " +
            "CASE WHEN :orderBy = '$LAST_UPDATED_AT' THEN last_updated_at END DESC")
    suspend fun loadDocumentsByParentId(parentId: String, orderBy: String): List<PdfDocumentEntity>

    @Query("SELECT * FROM $PDF_DOCUMENT_ENTITY WHERE parent_id = :parentId AND is_deleted = FALSE " +
            "ORDER BY last_updated_at DESC")
    fun listenForDocumentsByParentId(parentId: String): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM $PDF_DOCUMENT_ENTITY WHERE id = :id")
    fun listenForDocumentById(id: String): Flow<PdfDocumentEntity?>

    @Query("SELECT * FROM $PDF_DOCUMENT_ENTITY WHERE workspace_id = :workspaceId AND is_deleted = FALSE")
    suspend fun loadDocumentsByWorkspaceId(workspaceId: String): List<PdfDocumentEntity>

    @Query("SELECT * FROM $PDF_DOCUMENT_ENTITY")
    suspend fun loadAllDocuments(): List<PdfDocumentEntity>

    @Query("SELECT id FROM $PDF_DOCUMENT_ENTITY")
    suspend fun loadAllIds(): List<String>

    @Query("UPDATE $PDF_DOCUMENT_ENTITY SET workspace_id = :newWorkspaceId WHERE workspace_id = :oldWorkspaceId")
    suspend fun moveDocumentsToNewWorkspace(oldWorkspaceId: String, newWorkspaceId: String)

    @Query("SELECT title FROM $PDF_DOCUMENT_ENTITY WHERE id = :documentId")
    suspend fun getDocumentTitleById(documentId: String): String?

    // Hard delete: permanently removes PDF documents from database (scoped to workspace)
    @Query("DELETE FROM $PDF_DOCUMENT_ENTITY WHERE id IN (:ids) AND workspace_id = :workspaceId")
    suspend fun hardDeleteDocumentByIds(ids: List<String>, workspaceId: String)

    // Get soft-deleted PDF documents for a workspace (for syncing deletions to backend)
    @Query("SELECT * FROM $PDF_DOCUMENT_ENTITY WHERE workspace_id = :workspaceId AND is_deleted = TRUE")
    suspend fun getSoftDeletedByWorkspace(workspaceId: String): List<PdfDocumentEntity>
}
