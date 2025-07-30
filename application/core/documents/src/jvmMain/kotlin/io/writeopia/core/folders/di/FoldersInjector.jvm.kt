package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.folder.FolderRepositorySqlDelight
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.dao.FolderSqlDelightDao
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class FoldersInjector private constructor(
    private val writeopiaDb: WriteopiaDb?
) {
    private var folderDao: FolderSqlDelightDao? = null

    private fun provideFolderSqlDelightDao() = folderDao
        ?: FolderSqlDelightDao(writeopiaDb).also {
            folderDao = it
        }

    actual fun provideFoldersRepository(): FolderRepository =
        FolderRepositorySqlDelight(provideFolderSqlDelightDao())

    actual companion object {
        private var instance: FoldersInjector? = null

        actual fun singleton(): FoldersInjector =
            instance ?: FoldersInjector(WriteopiaDbInjector.singleton()?.database)
    }
}
