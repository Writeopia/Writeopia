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

    override suspend fun loadDocumentsForUser(userId: String): List<Document> =
        documentSqlDao.loadDocumentsWithContentByUserId(OrderBy.NAME.type, userId)

    override suspend fun loadDocumentsForFolder(folderId: String): List<Document> =
        documentSqlDao.loadDocumentByParentId(folderId)

    override suspend fun loadFavDocumentsForUser(orderBy: String, userId: String): List<Document> =
        documentSqlDao.loadFavDocumentsWithContentByUserId(orderBy, userId)

    override suspend fun loadDocumentsByParentId(parentId: String): List<Document> =
        documentSqlDao.loadDocumentByParentId(parentId)

    override suspend fun listenForDocumentsByParentId(
        parentId: String,
    ): Flow<Map<String, List<Document>>> {
        SelectedIds.ids.add(parentId)
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

    override suspend fun stopListeningForFoldersByParentId(parentId: String) {
        SelectedIds.ids.remove(parentId)
        refreshDocuments()
    }

    override suspend fun loadDocumentsForUserAfterTime(
        orderBy: String,
        userId: String,
        instant: Instant
    ): List<Document> {
        return documentSqlDao.loadDocumentsWithContentByUserIdAfterTime(
            userId,
            instant.toEpochMilliseconds()
        )
    }

    override suspend fun loadOutdatedDocuments(folderId: String): List<Document> =
        documentSqlDao.loadOutdatedDocumentByParentId(folderId)

    override suspend fun loadDocumentById(id: String): Document? =
        documentSqlDao.loadDocumentWithContentById(id)

    override suspend fun loadDocumentByIds(ids: List<String>): List<Document> =
        ids.mapNotNull { id ->
            loadDocumentById(id)
        }

    override suspend fun loadDocumentsWithContentByIds(
        ids: List<String>,
        orderBy: String
    ): List<Document> =
        documentSqlDao.loadDocumentWithContentByIds(ids)

    override suspend fun saveDocument(document: Document) {
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

    override suspend fun deleteByUserId(userId: String) {
        documentSqlDao.deleteDocumentsByUserId(userId)
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

    override suspend fun moveDocumentsToNewUser(oldUserId: String, newUserId: String) {
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
        _documentByParentState.value = SelectedIds.ids.associateWith { id ->
            documentSqlDao.loadDocumentByParentId(id)
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
