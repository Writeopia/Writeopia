package io.writeopia.sdk.persistence.sqldelight.dao

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.document.PdfDocument
import io.writeopia.sdk.persistence.sqldelight.toLong
import io.writeopia.sdk.sql.PdfDocumentEntityQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

class PdfDocumentSqlDao(
    private val pdfDocumentQueries: PdfDocumentEntityQueries?
) {

    suspend fun insertPdfDocument(pdfDocument: PdfDocument) {
        pdfDocumentQueries?.insert(
            id = pdfDocument.id,
            title = pdfDocument.title,
            file_path = pdfDocument.filePath,
            created_at = pdfDocument.createdAt.toEpochMilliseconds(),
            last_updated_at = pdfDocument.lastUpdatedAt.toEpochMilliseconds(),
            last_synced_at = pdfDocument.lastSyncedAt?.toEpochMilliseconds(),
            workspace_id = pdfDocument.workspaceId,
            favorite = pdfDocument.favorite.toLong(),
            parent_document_id = pdfDocument.parentId,
            icon = pdfDocument.icon?.label,
            deleted = pdfDocument.deleted.toLong()
        )
    }

    suspend fun loadPdfDocumentById(id: String): PdfDocument? {
        return pdfDocumentQueries?.selectById(id)
            ?.awaitAsOneOrNull()
            ?.let { entity ->
                PdfDocument(
                    id = entity.id,
                    title = entity.title,
                    filePath = entity.file_path,
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                    lastSyncedAt = entity.last_synced_at?.let(Instant::fromEpochMilliseconds),
                    workspaceId = entity.workspace_id,
                    favorite = entity.favorite == 1L,
                    parentId = entity.parent_document_id,
                    icon = entity.icon?.let { MenuItem.Icon(it, null) },
                    deleted = entity.deleted == 1L
                )
            }
    }

    suspend fun loadPdfDocumentsByIds(ids: List<String>, workspaceId: String): List<PdfDocument> {
        return pdfDocumentQueries?.selectByIds(ids, workspaceId)
            ?.awaitAsList()
            ?.map { entity ->
                PdfDocument(
                    id = entity.id,
                    title = entity.title,
                    filePath = entity.file_path,
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                    lastSyncedAt = entity.last_synced_at?.let(Instant::fromEpochMilliseconds),
                    workspaceId = entity.workspace_id,
                    favorite = entity.favorite == 1L,
                    parentId = entity.parent_document_id,
                    icon = entity.icon?.let { MenuItem.Icon(it, null) },
                    deleted = entity.deleted == 1L
                )
            } ?: emptyList()
    }

    suspend fun loadPdfDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): List<PdfDocument> {
        return pdfDocumentQueries?.selectByParentId(parentId, workspaceId)
            ?.awaitAsList()
            ?.map { entity ->
                PdfDocument(
                    id = entity.id,
                    title = entity.title,
                    filePath = entity.file_path,
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                    lastSyncedAt = entity.last_synced_at?.let(Instant::fromEpochMilliseconds),
                    workspaceId = entity.workspace_id,
                    favorite = entity.favorite == 1L,
                    parentId = entity.parent_document_id,
                    icon = entity.icon?.let { MenuItem.Icon(it, null) },
                    deleted = entity.deleted == 1L
                )
            } ?: emptyList()
    }

    suspend fun deletePdfDocuments(ids: Set<String>, workspaceId: String, timestamp: Long) {
        pdfDocumentQueries?.deleteByIds(timestamp, ids.toList(), workspaceId)
    }

    fun listenForPdfDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<List<PdfDocument>> {
        // Note: SqlDelight doesn't support Flow/observable queries in the async driver
        // For now, return empty flow - would need to implement polling or switch to Room
        return flowOf(emptyList())
    }
}
