package io.writeopia.di

import io.writeopia.LocalAiRepository
import io.writeopia.api.LocalAiApi
import io.writeopia.persistence.LocalAiDao
import io.writeopia.persistence.LocalAiSqlDao
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.di.WriteopiaDbInjector

class LocalAiInjection private constructor(
    private val appConnectionInjection: AppConnectionInjection,
    private val writeopiaDb: WriteopiaDb? = null,
) {

    var localAiDaoInstance: LocalAiDao? = null

    private fun provideLocalAiDao(): LocalAiDao = localAiDaoInstance ?: run {
        localAiDaoInstance = LocalAiSqlDao(writeopiaDb?.localAiEntityQueries)
        localAiDaoInstance!!
    }

    private fun provideApi() = LocalAiApi(
        client = appConnectionInjection.provideHttpClient(),
        json = appConnectionInjection.provideJson()
    )

    fun provideRepository(localAiApi: LocalAiApi = provideApi()) =
        LocalAiRepository(localAiApi, provideLocalAiDao())

    companion object {
        private var instance: LocalAiInjection? = null

        fun singleton() = instance ?: LocalAiInjection(
            appConnectionInjection = AppConnectionInjection.singleton(),
            writeopiaDb = WriteopiaDbInjector.singleton()?.database
        ).also {
            instance = it
        }
    }
}
