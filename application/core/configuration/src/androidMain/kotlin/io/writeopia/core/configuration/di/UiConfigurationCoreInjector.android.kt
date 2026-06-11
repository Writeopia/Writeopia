package io.writeopia.core.configuration.di

import io.writeopia.common.utils.di.SharedPreferencesInjector
import io.writeopia.repository.UiConfigurationPreferenceDao
import io.writeopia.repository.UiConfigurationRepository
import io.writeopia.repository.UiConfigurationRepositoryImpl

actual class UiConfigurationCoreInjector {
    actual fun provideUiConfigurationRepository(): UiConfigurationRepository =
        UiConfigurationRepositoryImpl(
            UiConfigurationPreferenceDao(
                SharedPreferencesInjector.singleton().sharedPreferences
            )
        )

    actual companion object {
        private var instance: UiConfigurationCoreInjector? = null

        actual fun singleton(): UiConfigurationCoreInjector =
            instance ?: UiConfigurationCoreInjector().also {
                instance = it
            }
    }

}
