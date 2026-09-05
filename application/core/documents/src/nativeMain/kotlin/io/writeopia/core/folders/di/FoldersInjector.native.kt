package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.folder.FolderRepositorySqlDelight
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.dao.FolderSqlDelightDao
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class FoldersInjector private constructor(
    private val writeopiaDb: WriteopiaDb?
) {
    private val lazyFolderDao = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FolderSqlDelightDao(writeopiaDb)
    }

    private fun provideFolderSqlDelightDao() = lazyFolderDao.value

    actual fun provideFoldersRepository(): FolderRepository =
        FolderRepositorySqlDelight(provideFolderSqlDelightDao())

    actual companion object {
        private val lazyInstance = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            FoldersInjector(WriteopiaDbInjector.singleton()?.database)
        }

        actual fun singleton(): FoldersInjector = lazyInstance.value
    }
}
