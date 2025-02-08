package io.writeopia.api.documents

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.persistence.sqldelight.dao.DocumentSqlDao
import io.writeopia.sdk.sql.WriteopiaDb

private var documentSqlDao: DocumentSqlDao? = null

private fun WriteopiaDb.getDocumentDao(): DocumentSqlDao =
    documentSqlDao ?: kotlin.run {
        DocumentSqlDao(
            documentEntityQueries,
            storyStepEntityQueries,
        ).also {
            documentSqlDao = it
        }
    }

suspend fun WriteopiaDb.saveDocument(vararg documents: Document) {
    documents.forEach { document ->
        getDocumentDao().insertDocumentWithContent(document)
    }
}

suspend fun WriteopiaDb.getDocumentsByParentId(parentId: String = "root"): List<Document> =
    getDocumentDao().loadDocumentByParentId(parentId)

suspend fun WriteopiaDb.getDocumentById(id: String = "test"): Document? =
    getDocumentDao().loadDocumentById(id)

suspend fun WriteopiaDb.getIdsByParentId(parentId: String = "root"): List<String> =
    getDocumentDao().loadDocumentIdsByParentId(parentId)

suspend fun WriteopiaDb.deleteDocumentById(vararg documentIds: String) {
    documentIds.forEach { id ->
        getDocumentDao().deleteDocumentById(id)
    }
}

