package io.writeopia.core.configuration.di

import io.writeopia.repository.UiConfigurationMemoryRepository
import io.writeopia.repository.UiConfigurationRepository

actual class UiConfigurationCoreInjector private constructor() {
    private var configRepository: UiConfigurationMemoryRepository? = null

    actual fun provideUiConfigurationRepository(): UiConfigurationRepository =
        getUiConfigurationMemoryRepository()

    private fun getUiConfigurationMemoryRepository(): UiConfigurationMemoryRepository =
        configRepository ?: kotlin.run {
            UiConfigurationMemoryRepository().also {
                configRepository = it
            }
            configRepository!!
        }

    actual companion object {
        private var instance: UiConfigurationCoreInjector? = null

        actual fun singleton(): UiConfigurationCoreInjector =
            instance ?: UiConfigurationCoreInjector().also {
                instance = it
            }
    }
}
