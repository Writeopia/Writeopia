package io.writeopia.core.folders.repository

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sqldelight.dao.StoryStepFtsSqlDelightDao

class InDocumentSearchSqlRepository(
    private val storyStepFtsSqlDelightDao: StoryStepFtsSqlDelightDao
) : InDocumentSearchRepository {

    override fun searchInDocument(query: String, documentId: String): Set<Int> = storyStepFtsSqlDelightDao.searchInDocument(
        query,
        documentId
    )

    override suspend fun insertForFts(storyStep: StoryStep, documentId: String, position: Double) {
        storyStepFtsSqlDelightDao.insertForFts(storyStep, documentId, position)
    }
}
