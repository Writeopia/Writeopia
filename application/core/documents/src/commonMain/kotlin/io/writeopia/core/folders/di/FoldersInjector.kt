package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.folder.FolderRepository

expect class FoldersInjector {

    fun provideFoldersRepository(): FolderRepository

    companion object {
        fun singleton(): FoldersInjector
    }
}
