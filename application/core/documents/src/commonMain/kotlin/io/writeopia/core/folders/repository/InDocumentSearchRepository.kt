package io.writeopia.core.folders.repository

import io.writeopia.sdk.models.story.StoryStep

interface InDocumentSearchRepository {

    fun searchInDocument(query: String, documentId: String): Set<Int>

    suspend fun insertForFts(storyStep: StoryStep, documentId: String, position: Int)
}
