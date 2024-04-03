package io.writeopia.sdk.persistence.core.di

import io.writeopia.sdk.persistence.core.repository.DocumentRepository
import io.writeopia.sdk.persistence.core.repository.NoopDocumentRepository

interface RepositoryInjector {

    fun provideDocumentRepository(): DocumentRepository

    companion object {
        fun noop(): RepositoryInjector = object : RepositoryInjector {
            override fun provideDocumentRepository(): DocumentRepository = NoopDocumentRepository()
        }
    }
}