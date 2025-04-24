package io.writeopia.features.search.di

import io.writeopia.di.AppConnectionInjection
import io.writeopia.features.search.api.SearchApi
import io.writeopia.features.search.repository.SearchRepository
import io.writeopia.features.search.ui.SearchKmpViewModel
import io.writeopia.models.search.FolderSearch
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.models.interfaces.search.FolderSearch
import io.writeopia.sdk.persistence.sqldelight.dao.DocumentSqlDao
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.dao.FolderSqlDelightDao
import io.writeopia.sqldelight.di.WriteopiaDbInjector

class KmpSearchInjection private constructor(
    private val writeopiaDb: WriteopiaDb? = null,
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton()
) : SearchInjection {

    private var folderDao: FolderSearch? = null
    private var documentDao: DocumentSearch? = null

    private fun provideFolderSqlDelightDao() = FolderSqlDelightDao(writeopiaDb)

    private fun provideDocumentSqlDao() = DocumentSqlDao(
        writeopiaDb?.documentEntityQueries,
        writeopiaDb?.storyStepEntityQueries
    )

    private fun provideSearchApi(): SearchApi =
        SearchApi(appConnectionInjection.provideHttpClient(), connectionInjector.baseUrl())

    fun provideRepository(
        folderDao: FolderSearch = provideFolderSqlDelightDao(),
        documentDao: DocumentSearch = provideDocumentSqlDao()
    ): SearchRepository = SearchRepository(
        this.folderDao ?: folderDao.also { this.folderDao = folderDao },
        this.documentDao ?: documentDao.also { this.documentDao = documentDao },
        provideSearchApi()
    )

    override fun provideViewModel(): SearchKmpViewModel =
        SearchKmpViewModel(searchRepository = provideRepository())

    companion object {
        private var instance: KmpSearchInjection? = null

        fun singleton() =
            instance ?: KmpSearchInjection(WriteopiaDbInjector.singleton()?.database).also {
                instance = it
            }
    }
}
