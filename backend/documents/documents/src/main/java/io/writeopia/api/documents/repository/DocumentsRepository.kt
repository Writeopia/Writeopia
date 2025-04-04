package io.writeopia.api.documents.repository

import io.writeopia.sdk.models.document.Document
import io.writeopia.sql.WriteopiaDbBackend

private var documentSqlDao: DocumentSqlBeDao? = null

private fun WriteopiaDbBackend.getDocumentDao(): DocumentSqlBeDao =
    documentSqlDao ?: kotlin.run {
        DocumentSqlBeDao(
            documentEntityQueries,
            storyStepEntityQueries,
        ).also {
            documentSqlDao = it
        }
    }

suspend fun WriteopiaDbBackend.saveDocument(vararg documents: Document) {
    documents.forEach { document ->
        getDocumentDao().insertDocumentWithContent(document)
    }
}

suspend fun WriteopiaDbBackend.getDocumentsByParentId(parentId: String = "root"): List<Document> =
    getDocumentDao().loadDocumentByParentId(parentId)

suspend fun WriteopiaDbBackend.getDocumentById(id: String = "test"): Document? =
    getDocumentDao().loadDocumentById(id)

suspend fun WriteopiaDbBackend.getIdsByParentId(parentId: String = "root"): List<String> =
    getDocumentDao().loadDocumentIdsByParentId(parentId)

suspend fun WriteopiaDbBackend.deleteDocumentById(vararg documentIds: String) {
    documentIds.forEach { id ->
        getDocumentDao().deleteDocumentById(id)
    }
}

