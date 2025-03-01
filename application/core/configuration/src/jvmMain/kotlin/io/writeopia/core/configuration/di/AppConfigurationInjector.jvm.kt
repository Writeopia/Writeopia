package io.writeopia.core.configuration.di

import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.configuration.repository.ConfigurationSqlDelightRepository
import io.writeopia.models.configuration.WorkspaceConfigRepository
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.dao.ConfigurationSqlDelightDao
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class AppConfigurationInjector private constructor(
    private val writeopiaDb: WriteopiaDb?
) {
    private var configurationRepository: ConfigurationRepository? = null
    private var configurationSqlDelightDao: ConfigurationSqlDelightDao? = null

    private fun provideNotesConfigurationSqlDelightDao() =
        configurationSqlDelightDao ?: ConfigurationSqlDelightDao(writeopiaDb).also {
            configurationSqlDelightDao = it
        }


    actual fun provideNotesConfigurationRepository(): ConfigurationRepository =
        configurationRepository ?: ConfigurationSqlDelightRepository(
            provideNotesConfigurationSqlDelightDao()
        ).also {
            configurationRepository = it
        }

    actual fun provideWorkspaceConfigRepository(): WorkspaceConfigRepository =
        provideNotesConfigurationRepository()

    actual companion object {
        private var instance: AppConfigurationInjector? = null

        actual fun singleton(): AppConfigurationInjector =
            instance ?: AppConfigurationInjector(WriteopiaDbInjector.singleton()?.database).also {
                instance = it
            }
    }
}
