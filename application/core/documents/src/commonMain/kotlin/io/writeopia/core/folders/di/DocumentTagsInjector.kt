package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.tag.DocumentTagRepository

expect class DocumentTagsInjector {

    fun provideDocumentTagRepository(): DocumentTagRepository

    companion object {
        fun singleton(): DocumentTagsInjector
    }
}
