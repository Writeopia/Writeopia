package io.writeopia.di

import io.writeopia.OllamaRepository
import io.writeopia.api.OllamaApi
import io.writeopia.persistence.OllamaDao
import io.writeopia.persistence.OllamaSqlDao
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.di.WriteopiaDbInjector

class OllamaInjection private constructor(
    private val appConnectionInjection: AppConnectionInjection,
    private val writeopiaDb: WriteopiaDb? = null,
) {

    var ollamaDaoInstance: OllamaDao? = null

    private fun provideOllamaDao(): OllamaDao = ollamaDaoInstance ?: run {
        ollamaDaoInstance = OllamaSqlDao(writeopiaDb?.ollamaEntityQueries)
        ollamaDaoInstance!!
    }

    private fun provideApi() = OllamaApi(
        client = appConnectionInjection.provideHttpClient(),
        json = appConnectionInjection.provideJson()
    )

    fun provideRepository(ollamaApi: OllamaApi = provideApi()) =
        OllamaRepository(ollamaApi, provideOllamaDao())

    companion object {
        private var instance: OllamaInjection? = null

        fun singleton() = instance ?: OllamaInjection(
            appConnectionInjection = AppConnectionInjection.singleton(),
            writeopiaDb = WriteopiaDbInjector.singleton()?.database
        ).also {
            instance = it
        }
    }
}
