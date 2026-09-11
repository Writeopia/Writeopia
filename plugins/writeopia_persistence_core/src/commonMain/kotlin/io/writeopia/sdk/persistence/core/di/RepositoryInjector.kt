package io.writeopia.sdk.persistence.core.di

import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.repository.PdfDocumentRepository

interface RepositoryInjector {

    fun provideDocumentRepository(): DocumentRepository

    fun providePdfDocumentRepository(): PdfDocumentRepository?

    companion object {
        private var instance: RepositoryInjector? = null

        fun initialize(injector: RepositoryInjector) {
            instance = injector
        }

        fun singleton(): RepositoryInjector =
            instance ?: throw IllegalStateException("RepositoryInjector not initialized")
    }
}
