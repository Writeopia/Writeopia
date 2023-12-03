package io.writeopia.sdk.persistence.sqldelight.dao.sql

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.persistence.core.repository.DocumentRepository
import io.writeopia.sdk.persistence.sqldelight.dao.DocumentSqlDao

class SqlDelightDocumentRepository(private val documentSqlDao: DocumentSqlDao) :
    DocumentRepository {
    override suspend fun loadDocumentsForUser(orderBy: String, userId: String): List<Document> {
        //Todo: Add orderby!
        return documentSqlDao.loadDocumentsWithContentByUserId(userId)
    }

    override suspend fun loadDocumentById(id: String): Document? =
        documentSqlDao.loadDocumentWithContentById(id)

    override suspend fun loadDocumentsWithContentByIds(
        ids: List<String>,
        orderBy: String
    ): List<Document> =
        documentSqlDao.loadDocumentWithContentByIds(ids)

    override suspend fun saveDocument(document: Document) {
        documentSqlDao.insertDocumentWithContent(document)
    }

    override suspend fun saveDocumentMetadata(document: Document) {
        documentSqlDao.insertDocument(document)
    }

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        documentSqlDao.insertStoryStep(storyStep, position.toLong(), documentId)
    }

    override suspend fun deleteDocument(document: Document) {
        documentSqlDao.deleteDocumentById(document.id)
    }

    override suspend fun deleteDocumentByIds(ids: Set<String>) {
        documentSqlDao.deleteDocumentByIds(ids)
    }

    override suspend fun deleteByUserId(userId: String) {
        documentSqlDao.deleteDocumentsByUserId(userId)
    }

    override suspend fun moveDocumentsToNewUser(oldUserId: String, newUserId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun updateStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        TODO("Not yet implemented")
    }
}