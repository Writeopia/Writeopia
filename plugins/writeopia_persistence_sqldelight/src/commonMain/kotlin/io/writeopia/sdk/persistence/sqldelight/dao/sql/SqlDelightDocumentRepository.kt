package io.writeopia.sdk.persistence.sqldelight.dao.sql

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.persistence.sqldelight.dao.DocumentSqlDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class SqlDelightDocumentRepository(
    private val documentSqlDao: DocumentSqlDao
) : DocumentRepository, DocumentSearch by documentSqlDao {

    private val _documentByParentState = MutableStateFlow<Map<String, List<Document>>>(emptyMap())

    override suspend fun loadDocumentsForFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> =
        documentSqlDao.loadDocumentByParentId(folderId, workspaceId)

    override suspend fun loadDocumentsWorkspace(workspaceId: String): List<Document> =
        documentSqlDao.loadDocumentsWithContentByWorkspaceId(OrderBy.NAME.type, workspaceId)

    override suspend fun loadFavDocumentsForWorkspace(
        orderBy: String,
        workspaceId: String
    ): List<Document> =
        documentSqlDao.loadFavDocumentsWithContentByUserId(orderBy, workspaceId)

    override suspend fun loadDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): List<Document> =
        documentSqlDao.loadDocumentByParentId(parentId, workspaceId)

    override suspend fun listenForDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Document>>> {
        SelectedIds.ids.add("$parentId:$workspaceId")
        refreshDocuments()

        return _documentByParentState
    }

    override suspend fun listenForDocumentInfoById(id: String): Flow<DocumentInfo?> {
        val document = documentSqlDao.loadDocumentById(id)

        if (document != null) {
            refreshDocument(document)
        }

        return _documentByParentState.map { documentMap ->
            documentMap.values
                .flatten()
                .find { document -> document.id == id }
                ?.info()
        }
    }

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {
        SelectedIds.ids.remove("$parentId:$workspaceId")
        refreshDocuments()
    }

    override suspend fun loadDocumentsForWorkspace(
        orderBy: String,
        workspaceId: String,
        instant: Instant
    ): List<Document> {
        return documentSqlDao.loadDocumentsWithContentByUserIdAfterTime(
            workspaceId,
            instant.toEpochMilliseconds()
        )
    }

    override suspend fun loadOutdatedDocumentsByFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> =
        documentSqlDao.loadOutdatedDocumentByParentId(folderId, workspaceId)

    override suspend fun loadOutdatedDocumentsForWorkspace(workspaceId: String): List<Document> =
        documentSqlDao.loadOutdatedDocumentByWorkspaceId(workspaceId)

    override suspend fun loadDocumentById(id: String, workspaceId: String): Document? =
        documentSqlDao.loadDocumentWithContentById(id, workspaceId)

    override suspend fun loadDocumentByIds(ids: List<String>, workspaceId: String): List<Document> =
        ids.mapNotNull { id ->
            loadDocumentById(id, workspaceId)
        }

    override suspend fun loadDocumentsWithContentByIds(
        ids: List<String>,
        orderBy: String,
        workspaceId: String
    ): List<Document> =
        documentSqlDao.loadDocumentWithContentByIds(ids)

    override suspend fun saveDocument(document: Document) {
        // Todo: Add company later
        documentSqlDao.insertDocumentWithContent(document)
        refreshDocuments()
    }

    override suspend fun saveDocumentMetadata(document: Document) {
        documentSqlDao.insertDocument(document)

        refreshDocuments()
    }

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        documentSqlDao.insertStoryStep(storyStep, position.toLong(), documentId)
    }

    override suspend fun deleteDocument(document: Document) {
        documentSqlDao.deleteDocumentById(document.id)

        refreshDocuments()
    }

    override suspend fun deleteDocumentByIds(ids: Set<String>) {
        documentSqlDao.deleteDocumentByIds(ids)

        refreshDocuments()
    }

    override suspend fun deleteDocumentByFolder(folderId: String) {
        documentSqlDao.deleteDocumentsByFolderId(folderId)
    }

    override suspend fun deleteByWorkspace(workspaceId: String) {
        documentSqlDao.deleteDocumentsByUserId(workspaceId)
    }

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {
        ids.forEach { id ->
            documentSqlDao.favoriteById(id)
        }

        refreshDocuments()
    }

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {
        ids.forEach { id ->
            documentSqlDao.unFavoriteById(id)
        }

        refreshDocuments()
    }

    override suspend fun moveDocumentsToWorkspace(oldUserId: String, newUserId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun updateStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
        documentSqlDao.moveToFolder(documentId, parentId)
        refreshDocuments()
    }

    override suspend fun refreshDocuments() {
        _documentByParentState.value = SelectedIds.ids.associateWith { key ->
            val (parentId, workspaceId) = key.split(":", limit = 2)
            documentSqlDao.loadDocumentByParentId(parentId, workspaceId)
        }
    }

    private fun refreshDocument(document: Document) {
        val documents = _documentByParentState.value
        val filtered = documents[document.parentId]?.filter { it.id != document.id }

        documents.toMutableMap()[document.parentId] = filtered?.plus(document) ?: emptyList()

        _documentByParentState.value = documents
    }
}

private object SelectedIds {
    val ids = mutableSetOf<String>()
}
