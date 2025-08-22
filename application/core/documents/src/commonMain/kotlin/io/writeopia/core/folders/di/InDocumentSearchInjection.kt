package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.InDocumentSearchRepository

expect class InDocumentSearchInjection {

    fun provideInDocumentSearchRepo(): InDocumentSearchRepository

    companion object {
        fun singleton(): InDocumentSearchInjection
    }
}
