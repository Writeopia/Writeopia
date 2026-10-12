package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.tag.DaoDocumentTagRepository
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.dao.DocumentTagSqlDelightDao
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class DocumentTagsInjector private constructor(
    private val writeopiaDb: WriteopiaDb?
) {
    private val lazyRepository = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DaoDocumentTagRepository(DocumentTagSqlDelightDao(writeopiaDb))
    }

    actual fun provideDocumentTagRepository(): DocumentTagRepository = lazyRepository.value

    actual companion object {
        private val lazyInstance = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            DocumentTagsInjector(WriteopiaDbInjector.singleton()?.database)
        }

        actual fun singleton(): DocumentTagsInjector = lazyInstance.value
    }
}
