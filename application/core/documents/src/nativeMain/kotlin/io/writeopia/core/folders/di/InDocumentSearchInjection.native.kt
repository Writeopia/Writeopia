package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.InDocumentSearchRepository

actual class InDocumentSearchInjection {
    actual fun provideInDocumentSearchRepo(): InDocumentSearchRepository {
        TODO("Not yet implemented")
    }

    actual companion object {
        actual fun singleton(): InDocumentSearchInjection {
            TODO("Not yet implemented")
        }
    }
}
