package io.writeopia.sdk.persistence.core.di

import io.writeopia.sdk.repository.DocumentRepository

interface RepositoryInjector {

    fun provideDocumentRepository(): DocumentRepository

    companion object {
        private var instance: RepositoryInjector? = null

        fun initialize(injector: RepositoryInjector) {
            instance = injector
        }

        fun singleton(): RepositoryInjector =
            instance ?: throw IllegalStateException("RepositoryInjector not initialized")
    }
}
