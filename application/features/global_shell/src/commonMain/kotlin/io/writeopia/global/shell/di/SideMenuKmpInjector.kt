package io.writeopia.global.shell.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.ai.AiClients
import io.writeopia.ai.UiConfigurationAiProviderStore
import io.writeopia.api.LocalAiAutoConfigApi
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.controller.LocalAiConfigController
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.configuration.di.UiConfigurationCoreInjector
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.MenuItemsRepository
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.core.folders.di.WorkspaceInjection
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.di.LocalAiConfigInjector
import io.writeopia.di.LocalAiInjection
import io.writeopia.genai.di.GenAiInjection
import io.writeopia.global.shell.menu.BackendSideMenuSource
import io.writeopia.global.shell.menu.LocalSideMenuSource
import io.writeopia.global.shell.menu.SideMenuSource
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
    private val localAiInjection: LocalAiInjection = LocalAiInjection.singleton(),
    private val workspaceInjection: WorkspaceInjection = WorkspaceInjection.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val genAiInjection: GenAiInjection? = GenAiInjection.singleton(),
    /** Fetches every folder from the backend, kept in memory: the webapp has no database. */
    private val menuItemsRepository: MenuItemsRepository? = null,
) : SideMenuInjector, LocalAiConfigInjector {
    // Always get fresh connection to handle logout/login scenarios
    private fun connectionInjector() = WriteopiaConnectionInjector.singleton()

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

    private fun provideSideMenuSource(notesUseCase: NotesUseCase): SideMenuSource =
        if (menuItemsRepository != null) {
            BackendSideMenuSource(
                menuItemsRepository = menuItemsRepository,
                authRepository = authCoreInjection.provideAuthRepository(),
                notesNavigationUseCase = NotesNavigationUseCase.singleton(),
            )
        } else {
            LocalSideMenuSource(
                notesUseCase = notesUseCase,
                authRepository = authCoreInjection.provideAuthRepository(),
                notesNavigationUseCase = NotesNavigationUseCase.singleton(),
                folderSync = workspaceInjection.provideFolderSync(),
            )
        }

    private fun provideDocumentsApi(): DocumentsApi =
        DocumentsApi(connectionInjector().httpClient(), connectionInjector().baseUrl())

    private fun provideLocalAiAutoConfigApi(): LocalAiAutoConfigApi =
        LocalAiAutoConfigApi(connectionInjector().httpClient(), connectionInjector().baseUrl())

    @Composable
    override fun provideSideMenuViewModel(keyboardEventFlow: Flow<KeyboardEvent>?): GlobalShellViewModel =
        viewModel {
            val notesUseCase = provideNotesUseCase()

            GlobalShellKmpViewModel(
                notesUseCase = notesUseCase,
                uiConfigurationRepo = UiConfigurationCoreInjector.singleton()
                    .provideUiConfigurationRepository(),
                authRepository = authCoreInjection.provideAuthRepository(),
                notesNavigationUseCase = NotesNavigationUseCase.singleton(),
                documentsApi = provideDocumentsApi(),
                localAiRepository = localAiInjection.provideRepository(),
                localAiAutoConfigApi = provideLocalAiAutoConfigApi(),
                authApi = authCoreInjection.provideAuthApi(),
                workspaceHandler = workspaceInjection.provideWorkspaceHandler(),
                keyboardEventFlow = keyboardEventFlow,
                genAiApi = genAiInjection?.provideGenAiApi(),
                sideMenuSource = provideSideMenuSource(notesUseCase),
                aiClients = AiClients(
                    localAiRepository = localAiInjection.provideRepository(),
                    genAiRepository = genAiInjection?.provideRepository(),
                    authRepository = authCoreInjection.provideAuthRepository(),
                    providerStore = UiConfigurationAiProviderStore(
                        UiConfigurationCoreInjector.singleton().provideUiConfigurationRepository()
                    ),
                ),
            )
        }

    @Composable
    override fun provideLocalAiConfigController(): LocalAiConfigController =
        provideSideMenuViewModel()
}
