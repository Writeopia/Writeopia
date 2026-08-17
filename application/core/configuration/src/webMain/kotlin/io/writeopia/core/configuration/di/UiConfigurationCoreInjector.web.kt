package io.writeopia.core.configuration.di

import io.writeopia.repository.UiConfigurationLocalStorageRepository
import io.writeopia.repository.UiConfigurationRepository

actual class UiConfigurationCoreInjector private constructor() {

    actual fun provideUiConfigurationRepository(): UiConfigurationRepository =
        UiConfigurationLocalStorageRepository.singleton()

    actual companion object {
        private var instance: UiConfigurationCoreInjector? = null

        actual fun singleton(): UiConfigurationCoreInjector =
            instance ?: UiConfigurationCoreInjector().also {
                instance = it
            }
    }
}
