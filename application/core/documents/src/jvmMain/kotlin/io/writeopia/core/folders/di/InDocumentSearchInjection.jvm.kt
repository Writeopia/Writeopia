package io.writeopia.core.folders.di

import io.writeopia.core.folders.repository.InDocumentSearchRepository
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.dao.StoryStepFtsSqlDelightDao
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class InDocumentSearchInjection private constructor(
    private val writeopiaDb: WriteopiaDb?
) {
    private var inDocumentSearchRepository: InDocumentSearchRepository? = null

    private fun provideFtsSearchDao(): StoryStepFtsSqlDelightDao =
        StoryStepFtsSqlDelightDao(writeopiaDb)

    actual fun provideInDocumentSearchRepo(): InDocumentSearchRepository =
        inDocumentSearchRepository ?: InDocumentSearchRepository(
            provideFtsSearchDao()
        ).also {
            inDocumentSearchRepository = it
        }

    actual companion object {
        private var instance: InDocumentSearchInjection? = null

        actual fun singleton(): InDocumentSearchInjection =
            instance ?: InDocumentSearchInjection(WriteopiaDbInjector.singleton()?.database)
    }
}
