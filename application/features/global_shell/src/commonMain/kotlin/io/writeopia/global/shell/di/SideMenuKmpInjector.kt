package io.writeopia.global.shell.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.controller.OllamaConfigController
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.configuration.di.UiConfigurationCoreInjector
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.di.OllamaConfigInjector
import io.writeopia.di.OllamaInjection
import io.writeopia.global.shell.viewmodel.GlobalShellKmpViewModel
import io.writeopia.global.shell.viewmodel.GlobalShellViewModel
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.core.folders.sync.DocumentConflictHandler
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sqldelight.di.SqlDelightDaoInjector

class SideMenuKmpInjector(
    private val appConfigurationInjector: AppConfigurationInjector =
        AppConfigurationInjector.singleton(),
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val repositoryInjection: RepositoryInjector = SqlDelightDaoInjector.singleton(),
    private val ollamaInjection: OllamaInjection = OllamaInjection.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton(),
) : SideMenuInjector, OllamaConfigInjector {
    private fun provideDocumentRepository(): DocumentRepository =
        repositoryInjection.provideDocumentRepository()

    private fun provideNotesUseCase(
        documentRepository: DocumentRepository = provideDocumentRepository(),
        configurationRepository: ConfigurationRepository =
            appConfigurationInjector.provideNotesConfigurationRepository(),
        folderRepository: FolderRepository = FoldersInjector.singleton().provideFoldersRepository(),
    ): NotesUseCase {
        return NotesUseCase.singleton(
            documentRepository,
            configurationRepository,
            folderRepository,
            authCoreInjection.provideAuthRepository()
        )
    }

    private fun provideWorkspaceSync(): WorkspaceSync {
        val documentRepo = repositoryInjection.provideDocumentRepository()
        return WorkspaceSync(
            folderRepository = FoldersInjector.singleton().provideFoldersRepository(),
            documentRepository = documentRepo,
            authRepository = authCoreInjection.provideAuthRepository(),
            documentsApi = DocumentsApi(
                appConnectionInjection.provideHttpClient(),
                connectionInjector.baseUrl()
            ),
            documentConflictHandler = DocumentConflictHandler(
                documentRepository = documentRepo,
                folderRepository = FoldersInjector.singleton().provideFoldersRepository(),
                authCoreInjection.provideAuthRepository()
            ),
        )
    }

    private fun provideWorkspaceApi() =
        WorkspaceApi(appConnectionInjection.provideHttpClient(), connectionInjector.baseUrl())

    @Composable
    override fun provideSideMenuViewModel(): GlobalShellViewModel =
        viewModel {
            GlobalShellKmpViewModel(
                notesUseCase = provideNotesUseCase(),
                uiConfigurationRepo = UiConfigurationCoreInjector.singleton()
                    .provideUiConfigurationRepository(),
                authRepository = authCoreInjection.provideAuthRepository(),
                notesNavigationUseCase = NotesNavigationUseCase.singleton(),
                workspaceConfigRepository = appConfigurationInjector.provideNotesConfigurationRepository(),
                ollamaRepository = ollamaInjection.provideRepository(),
                configRepository = appConfigurationInjector.provideNotesConfigurationRepository(),
                authApi = authCoreInjection.provideAuthApi(),
                workspaceSync = provideWorkspaceSync(),
                workspaceApi = provideWorkspaceApi()
            )
        }

    @Composable
    override fun provideOllamaConfigController(): OllamaConfigController =
        provideSideMenuViewModel()
}
