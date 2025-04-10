package io.writeopia.notemenu.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.core.folders.repository.FolderRepository
import io.writeopia.core.folders.sync.DocumentConflictHandler
import io.writeopia.core.folders.sync.DocumentsSync
import io.writeopia.di.AppConnectionInjection
import io.writeopia.notemenu.data.model.NotesNavigation
import io.writeopia.notemenu.data.usecase.NotesUseCase
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.repository.NotesUseCase
import io.writeopia.notemenu.viewmodel.ChooseNoteKmpViewModel
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel
import io.writeopia.notemenu.viewmodel.FolderStateController
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotesMenuKmpInjection private constructor(
    private val appConfigurationInjector: AppConfigurationInjector = AppConfigurationInjector.singleton(),
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val repositoryInjection: RepositoryInjector,
    private val selectionState: StateFlow<Boolean>,
    private val keyboardEventFlow: Flow<KeyboardEvent>,
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton()
) : NotesMenuInjection {

    private fun provideDocumentRepository(): DocumentRepository =
        repositoryInjection.provideDocumentRepository()

    private fun provideNotesUseCase(
        documentRepository: DocumentRepository = provideDocumentRepository(),
        configurationRepository: ConfigurationRepository =
            appConfigurationInjector.provideNotesConfigurationRepository(),
        folderRepository: FolderRepository = FoldersInjector.singleton().provideFoldersRepository()
    ): NotesUseCase {
        return NotesUseCase.singleton(documentRepository, configurationRepository, folderRepository)
    }

    private fun provideFolderStateController(): FolderStateController =
        FolderStateController(
            provideNotesUseCase(),
            authCoreInjection.provideAccountManager()
        )

    private fun provideDocumentsApi() =
        DocumentsApi(appConnectionInjection.provideHttpClient(), connectionInjector.baseUrl())

    private fun provideDocumentSync(): DocumentsSync {
        val documentRepository = repositoryInjection.provideDocumentRepository()

        return DocumentsSync(
            documentRepository = documentRepository,
            documentsApi = provideDocumentsApi(),
            documentConflictHandler = DocumentConflictHandler(documentRepository)
        )
    }

    private fun provideChooseKmpNoteViewModel(
        notesNavigation: NotesNavigation,
        notesUseCase: NotesUseCase = provideNotesUseCase(),
        notesConfig: ConfigurationRepository =
            appConfigurationInjector.provideNotesConfigurationRepository(),
    ): ChooseNoteKmpViewModel =
        ChooseNoteKmpViewModel(
            notesUseCase = notesUseCase,
            notesConfig = notesConfig,
            authManager = authCoreInjection.provideAccountManager(),
            selectionState = selectionState,
            notesNavigation = notesNavigation,
            folderController = provideFolderStateController(),
            keyboardEventFlow = keyboardEventFlow,
            workspaceConfigRepository = appConfigurationInjector.provideWorkspaceConfigRepository(),
            documentsSync = provideDocumentSync(),
        )

    @Composable
    override fun provideChooseNoteViewModel(
        notesNavigation: NotesNavigation
    ): ChooseNoteViewModel =
        viewModel {
            provideChooseKmpNoteViewModel(notesNavigation)
        }

    companion object {
        private var instanceMobile: NotesMenuKmpInjection? = null
        private var instanceDesktop: NotesMenuKmpInjection? = null

        fun mobile(
            repositoryInjection: RepositoryInjector,
        ) = instanceMobile ?: NotesMenuKmpInjection(
            repositoryInjection = repositoryInjection,
            selectionState = MutableStateFlow(false),
            keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE)
        ).also {
            instanceMobile = it
        }

        fun desktop(
            selectionState: StateFlow<Boolean>,
            keyboardEventFlow: Flow<KeyboardEvent>,
            repositoryInjection: RepositoryInjector = SqlDelightDaoInjector.singleton(),
        ) = instanceDesktop ?: NotesMenuKmpInjection(
            repositoryInjection = repositoryInjection,
            selectionState = selectionState,
            keyboardEventFlow = keyboardEventFlow,
        ).also {
            instanceDesktop = it
        }
    }
}
