package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.InDocumentSearchRepository
import io.writeopia.sdk.models.story.StoryStep

actual class InDocumentSearchInjection {
    actual fun provideInDocumentSearchRepo(): InDocumentSearchRepository {
        return object : InDocumentSearchRepository {
            override fun searchInDocument(
                query: String,
                documentId: String
            ): Set<Int> = emptySet()

            override suspend fun insertForFts(
                storyStep: StoryStep,
                documentId: String,
                position: Int
            ) {}
        }
    }

    actual companion object {
        private var instance: InDocumentSearchInjection? = null

        actual fun singleton(): InDocumentSearchInjection =
            instance ?: InDocumentSearchInjection()
    }
}
