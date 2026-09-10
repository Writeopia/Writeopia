package io.writeopia.sdk.persistence.sqldelight.dao.sql

import io.writeopia.sdk.models.document.PdfDocument
import io.writeopia.sdk.repository.PdfDocumentRepository
import io.writeopia.sdk.persistence.sqldelight.dao.PdfDocumentSqlDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class SqlDelightPdfDocumentRepository(
    private val pdfDocumentSqlDao: PdfDocumentSqlDao
) : PdfDocumentRepository {

    private val _pdfDocumentsByParentState = MutableStateFlow<Map<String, List<PdfDocument>>>(emptyMap())

    override suspend fun savePdfDocument(pdfDocument: PdfDocument) {
        pdfDocumentSqlDao.insertPdfDocument(pdfDocument)
        refreshPdfDocuments()
    }

    override suspend fun loadPdfDocumentById(id: String, workspaceId: String): PdfDocument? {
        return pdfDocumentSqlDao.loadPdfDocumentById(id)
    }

    override suspend fun loadPdfDocumentsByIds(ids: List<String>, workspaceId: String): List<PdfDocument> {
        return pdfDocumentSqlDao.loadPdfDocumentsByIds(ids, workspaceId)
    }

    override suspend fun loadPdfDocumentsByParentId(
        parentId: String,
        workspaceId: String,
        orderBy: String
    ): List<PdfDocument> {
        return pdfDocumentSqlDao.loadPdfDocumentsByParentId(parentId, workspaceId)
    }

    override suspend fun listenForPdfDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<List<PdfDocument>> {
        val key = "$parentId:$workspaceId"
        SelectedPdfIds.ids.add(key)
        refreshPdfDocuments()

        return _pdfDocumentsByParentState.map { pdfMap ->
            pdfMap[key] ?: emptyList()
        }
    }

    override suspend fun deletePdfDocuments(ids: Set<String>, workspaceId: String) {
        pdfDocumentSqlDao.deletePdfDocuments(ids, workspaceId, Clock.System.now().toEpochMilliseconds())
        refreshPdfDocuments()
    }

    override suspend fun refreshPdfDocuments() {
        val allPdfs = mutableMapOf<String, MutableList<PdfDocument>>()

        SelectedPdfIds.ids.forEach { key ->
            val (parentId, workspaceId) = key.split(":")
            val pdfs = pdfDocumentSqlDao.loadPdfDocumentsByParentId(parentId, workspaceId)
            allPdfs[key] = pdfs.toMutableList()
        }

        _pdfDocumentsByParentState.value = allPdfs
    }
}

private object SelectedPdfIds {
    val ids = mutableSetOf<String>()
}
