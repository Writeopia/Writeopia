package io.writeopia.global.shell.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.controller.OllamaConfigController
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.configuration.di.UiConfigurationCoreInjector
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.core.folders.repository.FolderRepository
import io.writeopia.di.OllamaConfigInjector
import io.writeopia.di.OllamaInjection
import io.writeopia.global.shell.viewmodel.GlobalShellKmpViewModel
import io.writeopia.global.shell.viewmodel.GlobalShellViewModel
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.core.folders.repository.NotesUseCase
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sqldelight.di.SqlDelightDaoInjector

class SideMenuKmpInjector(
    private val appConfigurationInjector: AppConfigurationInjector = AppConfigurationInjector.singleton(),
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val repositoryInjection: RepositoryInjector = SqlDelightDaoInjector.singleton(),
    private val ollamaInjection: OllamaInjection = OllamaInjection.singleton(),
) : SideMenuInjector, OllamaConfigInjector {
    private fun provideDocumentRepository(): DocumentRepository =
        repositoryInjection.provideDocumentRepository()

    private fun provideNotesUseCase(
        documentRepository: DocumentRepository = provideDocumentRepository(),
        configurationRepository: ConfigurationRepository =
            appConfigurationInjector.provideNotesConfigurationRepository(),
        folderRepository: FolderRepository = FoldersInjector.singleton().provideFoldersRepository(),
    ): NotesUseCase {
        return NotesUseCase.singleton(documentRepository, configurationRepository, folderRepository)
    }

    @Composable
    override fun provideSideMenuViewModel(): GlobalShellViewModel =
        viewModel {
            GlobalShellKmpViewModel(
                notesUseCase = provideNotesUseCase(),
                uiConfigurationRepo = UiConfigurationCoreInjector.singleton()
                    .provideUiConfigurationRepository(),
                authManager = authCoreInjection.provideAccountManager(),
                notesNavigationUseCase = NotesNavigationUseCase.singleton(),
                workspaceConfigRepository = appConfigurationInjector.provideNotesConfigurationRepository(),
                ollamaRepository = ollamaInjection.provideRepository(),
                configRepository = appConfigurationInjector.provideNotesConfigurationRepository()
            )
        }

    @Composable
    override fun provideOllamaConfigController(): OllamaConfigController =
        provideSideMenuViewModel()
}
