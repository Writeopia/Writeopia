package io.writeopia.global.shell.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.controller.OllamaConfigController
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.configuration.di.UiConfigurationCoreInjector
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.core.folders.di.WorkspaceInjection
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.di.OllamaConfigInjector
import io.writeopia.di.OllamaInjection
import io.writeopia.global.shell.viewmodel.GlobalShellKmpViewModel
import io.writeopia.global.shell.viewmodel.GlobalShellViewModel
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.flow.Flow

class SideMenuKmpInjector(
    private val appConfigurationInjector: AppConfigurationInjector =
        AppConfigurationInjector.singleton(),
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val repositoryInjection: RepositoryInjector = RepositoryInjector.singleton(),
    private val ollamaInjection: OllamaInjection = OllamaInjection.singleton(),
    private val workspaceInjection: WorkspaceInjection = WorkspaceInjection.singleton(),
) : SideMenuInjector, OllamaConfigInjector {
    private fun provideDocumentRepository(): DocumentRepository =
        repositoryInjection.provideDocumentRepository()

    private fun provideNotesUseCase(
        documentRepository: DocumentRepository = provideDocumentRepository(),
        configurationRepository: ConfigurationRepository =
            appConfigurationInjector.provideNotesConfigurationRepository(),
        folderRepository: FolderRepository = FoldersInjector.singleton().provideFoldersRepository(),
    ): NotesUseCase =
        NotesUseCase.singleton(
            documentRepository,
            configurationRepository,
            folderRepository,
        )

    @Composable
    override fun provideSideMenuViewModel(keyboardEventFlow: Flow<KeyboardEvent>?): GlobalShellViewModel =
        viewModel {
            GlobalShellKmpViewModel(
                notesUseCase = provideNotesUseCase(),
                uiConfigurationRepo = UiConfigurationCoreInjector.singleton()
                    .provideUiConfigurationRepository(),
                authRepository = authCoreInjection.provideAuthRepository(),
                notesNavigationUseCase = NotesNavigationUseCase.singleton(),
                ollamaRepository = ollamaInjection.provideRepository(),
                authApi = authCoreInjection.provideAuthApi(),
                workspaceHandler = workspaceInjection.provideWorkspaceHandler(),
                keyboardEventFlow = keyboardEventFlow
            )
        }

    @Composable
    override fun provideOllamaConfigController(): OllamaConfigController =
        provideSideMenuViewModel()
}
