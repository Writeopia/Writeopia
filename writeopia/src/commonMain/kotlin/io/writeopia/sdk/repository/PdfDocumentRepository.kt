@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.repository

import io.writeopia.sdk.models.document.PdfDocument
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime

/**
 * Repository for PDF documents.
 * PDF documents are stored separately from regular documents as they
 * don't contain rich text content, only file references.
 */
interface PdfDocumentRepository {

    suspend fun savePdfDocument(pdfDocument: PdfDocument)

    suspend fun loadPdfDocumentById(id: String, workspaceId: String): PdfDocument?

    suspend fun loadPdfDocumentsByIds(ids: List<String>, workspaceId: String): List<PdfDocument>

    suspend fun loadPdfDocumentsByParentId(parentId: String, workspaceId: String, orderBy: String): List<PdfDocument>

    suspend fun listenForPdfDocumentsByParentId(parentId: String, workspaceId: String): Flow<List<PdfDocument>>

    suspend fun deletePdfDocuments(ids: Set<String>, workspaceId: String)

    suspend fun refreshPdfDocuments()
}
