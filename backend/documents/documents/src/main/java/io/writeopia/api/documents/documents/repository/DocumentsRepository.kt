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
    val dao = getDocumentDaoFn()
    documents.forEach(dao::insertDocumentWithContent)
}

suspend fun WriteopiaDbBackend.saveFolder(vararg folders: Folder) {
    val dao = getDocumentDaoFn()
    folders.forEach(dao::insertFolder)
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
): Document? = getDocumentDaoFn().loadDocumentById(id, workspaceId)

suspend fun WriteopiaDbBackend.getFolderById(id: String = "test", userId: String): Folder? =
    getDocumentDaoFn().loadFolderById(id)

suspend fun WriteopiaDbBackend.getIdsByParentId(parentId: String = "root"): List<String> =
    getDocumentDaoFn().loadDocumentIdsByParentId(parentId)

fun WriteopiaDbBackend.getFoldersByParentId(parentId: String = "root"): List<Folder> =
    getDocumentDaoFn().loadFoldersByParentId(parentId)

suspend fun WriteopiaDbBackend.deleteDocumentById(vararg documentIds: String) {
    val dao = getDocumentDaoFn()
    documentIds.forEach(dao::deleteDocumentById)
}

fun WriteopiaDbBackend.deleteFolder(folderId: String) {
    getDocumentDaoFn().deleteFolder(folderId)
}

fun WriteopiaDbBackend.deleteDocumentsByFolderId(folderId: String) {
    getDocumentDaoFn().deleteDocumentsByFolderId(folderId)
}

fun WriteopiaDbBackend.moveFolderToFolder(folderId: String, parentId: String) {
    getDocumentDaoFn().moveFolderToFolder(folderId, parentId)
}

suspend fun WriteopiaDbBackend.deleteDocumentsByIds(documentIds: List<String>) {
    val dao = getDocumentDaoFn()
    dao.deleteDocumentByIds(documentIds.toSet())
}
