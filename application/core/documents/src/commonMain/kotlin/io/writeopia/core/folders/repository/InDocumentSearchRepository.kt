package io.writeopia.core.folders.repository

import io.writeopia.sqldelight.dao.StoryStepFtsSqlDelightDao

class InDocumentSearchRepository(private val storyStepFtsSqlDelightDao: StoryStepFtsSqlDelightDao) {

    fun searchInDocument(query: String, documentId: String): List<Int> {
        return storyStepFtsSqlDelightDao.searchInDocument(query, documentId)
    }
}
