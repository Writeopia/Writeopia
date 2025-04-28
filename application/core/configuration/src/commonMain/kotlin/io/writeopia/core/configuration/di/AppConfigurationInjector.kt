package io.writeopia.core.configuration.di

import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository

expect class AppConfigurationInjector {

    fun provideNotesConfigurationRepository(): ConfigurationRepository

    fun provideWorkspaceConfigRepository(): WorkspaceConfigRepository

    companion object {
        fun singleton(): AppConfigurationInjector
    }
}
