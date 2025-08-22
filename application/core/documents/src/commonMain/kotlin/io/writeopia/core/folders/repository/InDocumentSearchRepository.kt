package io.writeopia.core.folders.repository

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sqldelight.dao.StoryStepFtsSqlDelightDao

class InDocumentSearchRepository(private val storyStepFtsSqlDelightDao: StoryStepFtsSqlDelightDao) {

    fun searchInDocument(query: String, documentId: String): Set<Int> {
        return storyStepFtsSqlDelightDao.searchInDocument(query, documentId)
    }

    suspend fun insertForFts(storyStep: StoryStep, documentId: String, position: Int) {
        storyStepFtsSqlDelightDao.insertForFts(storyStep, documentId, position)
    }
}
