@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.dao.room

import io.writeopia.sdk.models.document.PdfDocument
import io.writeopia.sdk.persistence.dao.PdfDocumentEntityDao
import io.writeopia.sdk.persistence.parse.toEntity
import io.writeopia.sdk.persistence.parse.toModel
import io.writeopia.sdk.repository.PdfDocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime

class RoomPdfDocumentRepository(
    private val pdfDocumentEntityDao: PdfDocumentEntityDao
) : PdfDocumentRepository {

    private val pdfDocumentsRefreshState: MutableStateFlow<Long> = MutableStateFlow(0L)

    override suspend fun savePdfDocument(pdfDocument: PdfDocument) {
        pdfDocumentEntityDao.insertDocuments(pdfDocument.toEntity())
        refreshPdfDocuments()
    }

    override suspend fun loadPdfDocumentById(id: String, workspaceId: String): PdfDocument? =
        pdfDocumentEntityDao.loadDocumentById(id)
            ?.takeIf { it.workspaceId == workspaceId }
            ?.toModel()

    override suspend fun loadPdfDocumentsByIds(ids: List<String>, workspaceId: String): List<PdfDocument> =
        pdfDocumentEntityDao.loadDocumentByIds(ids)
            .filter { it.workspaceId == workspaceId }
            .map { it.toModel() }

    override suspend fun loadPdfDocumentsByParentId(
        parentId: String,
        workspaceId: String,
        orderBy: String
    ): List<PdfDocument> =
        pdfDocumentEntityDao.loadDocumentsByParentId(parentId, orderBy)
            .filter { it.workspaceId == workspaceId }
            .map { it.toModel() }

    override suspend fun listenForPdfDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<List<PdfDocument>> =
        pdfDocumentEntityDao.listenForDocumentsByParentId(parentId)
            .map { entities ->
                entities.filter { it.workspaceId == workspaceId }
                    .map { it.toModel() }
            }

    override suspend fun deletePdfDocuments(ids: Set<String>, workspaceId: String) {
        val documentsToDelete = ids.mapNotNull { id ->
            pdfDocumentEntityDao.loadDocumentById(id)
        }.filter { it.workspaceId == workspaceId }
            .map { doc -> doc.copy(isDeleted = true) }

        if (documentsToDelete.isNotEmpty()) {
            pdfDocumentEntityDao.updateDocument(*documentsToDelete.toTypedArray())
            refreshPdfDocuments()
        }
    }

    override suspend fun refreshPdfDocuments() {
        pdfDocumentsRefreshState.value = System.currentTimeMillis()
    }
}
