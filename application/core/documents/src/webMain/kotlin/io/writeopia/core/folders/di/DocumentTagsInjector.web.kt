package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.core.folders.repository.tag.InMemoryDocumentTagRepository

actual class DocumentTagsInjector {

    actual fun provideDocumentTagRepository(): DocumentTagRepository =
        InMemoryDocumentTagRepository.singleton()

    actual companion object {
        private val instance = DocumentTagsInjector()

        actual fun singleton(): DocumentTagsInjector = instance
    }
}
