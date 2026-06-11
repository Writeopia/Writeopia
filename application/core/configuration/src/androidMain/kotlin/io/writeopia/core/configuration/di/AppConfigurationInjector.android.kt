package io.writeopia.core.configuration.di

import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.configuration.repository.ConfigurationRoomRepository
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import io.writeopia.persistence.room.injection.AppRoomDaosInjection

actual class AppConfigurationInjector private constructor(
    private val appRoomDaosInjection: AppDaosInjection
) {

    private var configurationRepository: ConfigurationRepository? = null

    actual fun provideNotesConfigurationRepository(): ConfigurationRepository =
        configurationRepository ?: kotlin.run {
            ConfigurationRoomRepository(appRoomDaosInjection.provideConfigurationDao()).also {
                configurationRepository = it
            }
        }

    actual fun provideWorkspaceConfigRepository(): WorkspaceConfigRepository =
        provideNotesConfigurationRepository()

    actual companion object {
        private var instance: AppConfigurationInjector? = null

        actual fun singleton(): AppConfigurationInjector =
            instance ?: AppConfigurationInjector(AppRoomDaosInjection.singleton()).also {
                instance = it
            }
    }
}
