package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.folder.InMemoryFolderRepository

actual class FoldersInjector {

    actual fun provideFoldersRepository(): FolderRepository =
        InMemoryFolderRepository.singleton()

    actual companion object {
        private fun noop() = FoldersInjector()

        actual fun singleton(): FoldersInjector = noop()
    }
}
