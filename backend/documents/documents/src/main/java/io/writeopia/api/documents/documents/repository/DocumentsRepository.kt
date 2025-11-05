package io.writeopia.api.documents.documents.repository

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sql.WriteopiaDbBackend

private var documentSqlDao: DocumentSqlBeDao? = null

private fun WriteopiaDbBackend.getDocumentDaoFn(): DocumentSqlBeDao =
    documentSqlDao ?: kotlin.run {
        DocumentSqlBeDao(
            documentEntityQueries,
            storyStepEntityQueries,
            folderEntityQueries
        ).also {
            documentSqlDao = it
        }
    }

suspend fun WriteopiaDbBackend.saveDocument(vararg documents: Document) {
    documents.forEach { document ->
        getDocumentDaoFn().insertDocumentWithContent(document)
    }
}

suspend fun WriteopiaDbBackend.saveFolder(vararg folders: Folder) {
    folders.forEach { folder -> getDocumentDaoFn().insertFolder(folder) }
}

suspend fun WriteopiaDbBackend.documentsDiffByFolder(
    folderId: String,
    workspaceId: String,
    lastSync: Long
): List<Document> =
    getDocumentDaoFn().loadDocumentsWithContentFolderIdAfterTime(folderId, workspaceId, lastSync)

suspend fun WriteopiaDbBackend.documentsDiffByWorkspace(
    workspaceId: String,
    lastSync: Long
): List<Document> =
    getDocumentDaoFn().loadDocumentsWithContentByWorkspaceIdAfterTime(workspaceId, lastSync)

suspend fun WriteopiaDbBackend.allFoldersByWorkspaceId(workspaceId: String): List<Folder> {
    return getDocumentDaoFn().loadAllFoldersByWorkspaceId(workspaceId)
}

fun WriteopiaDbBackend.getDocumentsByParentId(parentId: String = "root"): List<Document> =
    getDocumentDaoFn().loadDocumentByParentId(parentId)

suspend fun WriteopiaDbBackend.getDocumentById(
    id: String = "test",
    workspaceId: String
): Document? =
    getDocumentDaoFn().loadDocumentById(id, workspaceId)

suspend fun WriteopiaDbBackend.getFolderById(id: String = "test", userId: String): Folder? =
    getDocumentDaoFn().loadFolderById(id)

suspend fun WriteopiaDbBackend.getIdsByParentId(parentId: String = "root"): List<String> =
    getDocumentDaoFn().loadDocumentIdsByParentId(parentId)

suspend fun WriteopiaDbBackend.deleteDocumentById(vararg documentIds: String) {
    documentIds.forEach { id ->
        getDocumentDaoFn().deleteDocumentById(id)
    }
}
