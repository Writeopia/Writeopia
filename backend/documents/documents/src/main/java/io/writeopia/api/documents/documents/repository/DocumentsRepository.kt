package io.writeopia.api.documents.documents.repository

import io.writeopia.sdk.models.document.Document
import io.writeopia.sql.WriteopiaDbBackend

private var documentSqlDao: DocumentSqlBeDao? = null

private fun WriteopiaDbBackend.getDocumentDaoFn(): DocumentSqlBeDao =
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
        getDocumentDaoFn().insertDocumentWithContent(document)
    }
}

suspend fun WriteopiaDbBackend.folderDiff(
    folderId: String,
    userId: String,
    lastSync: Long
): List<Document> =
    getDocumentDaoFn().loadDocumentsWithContentFolderIdAfterTime(folderId, userId, lastSync)

suspend fun WriteopiaDbBackend.getDocumentsByParentId(parentId: String = "root"): List<Document> =
    getDocumentDaoFn().loadDocumentByParentId(parentId)

suspend fun WriteopiaDbBackend.getDocumentById(id: String = "test"): Document? =
    getDocumentDaoFn().loadDocumentById(id)

suspend fun WriteopiaDbBackend.getIdsByParentId(parentId: String = "root"): List<String> =
    getDocumentDaoFn().loadDocumentIdsByParentId(parentId)

suspend fun WriteopiaDbBackend.deleteDocumentById(vararg documentIds: String) {
    documentIds.forEach { id ->
        getDocumentDaoFn().deleteDocumentById(id)
    }
}
