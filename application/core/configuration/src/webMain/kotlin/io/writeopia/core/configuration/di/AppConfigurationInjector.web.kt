package io.writeopia.core.configuration.di

import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.configuration.repository.InMemoryConfigurationRepository
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository

actual class AppConfigurationInjector {
    actual fun provideNotesConfigurationRepository(): ConfigurationRepository =
        InMemoryConfigurationRepository.singleton()

    actual fun provideWorkspaceConfigRepository(): WorkspaceConfigRepository {
        return provideNotesConfigurationRepository()
    }

    actual companion object {
        private fun noop() = AppConfigurationInjector()

        actual fun singleton(): AppConfigurationInjector = noop()
    }
}
