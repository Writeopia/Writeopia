package io.writeopia.editor.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.configuration.di.UiConfigurationCoreInjector
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.di.OllamaInjection
import io.writeopia.editor.features.editor.copy.CopyManager
import io.writeopia.editor.features.editor.viewmodel.NoteEditorKmpViewModel
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.presentation.viewmodel.PresentationKmpViewModel
import io.writeopia.editor.features.presentation.viewmodel.PresentationViewModel
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.sharededition.SharedEditionManager
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import io.writeopia.ui.keyboard.KeyboardEvent
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EditorKmpInjector private constructor(
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val repositoryInjection: RepositoryInjector,
    private val connectionInjection: WriteopiaConnectionInjector,
    private val selectionState: StateFlow<Boolean>,
    private val keyboardEventFlow: Flow<KeyboardEvent>,
    private val appConfigurationInjector: AppConfigurationInjector =
        AppConfigurationInjector.singleton(),
    private val ollamaInjection: OllamaInjection? = null
) : TextEditorInjector {

    private fun provideDocumentRepository(): DocumentRepository =
        repositoryInjection.provideDocumentRepository()

    private fun provideWriteopiaManager(): WriteopiaManager = WriteopiaManager()

    private fun provideWriteopiaStateManager(
        authRepository: AuthRepository = authCoreInjection.provideAuthRepository(),
        writeopiaManager: WriteopiaManager = provideWriteopiaManager()
    ) = WriteopiaStateManager.create(
        dispatcher = Dispatchers.Default,
        writeopiaManager = writeopiaManager,
        selectionState = selectionState,
        keyboardEventFlow = keyboardEventFlow,
        documentRepository = repositoryInjection.provideDocumentRepository(),
        userRepository = authRepository
    )

    private fun provideNoteEditorViewModel(
        documentRepository: DocumentRepository = provideDocumentRepository(),
        writeopiaManager: WriteopiaStateManager = provideWriteopiaStateManager(),
        sharedEditionManager: SharedEditionManager = connectionInjection.liveEditionManager(),
        parentFolder: String,
        copyManager: CopyManager,
    ): NoteEditorKmpViewModel =
        NoteEditorKmpViewModel(
            writeopiaManager,
            documentRepository,
            sharedEditionManager = sharedEditionManager,
            parentFolderId = parentFolder,
            uiConfigurationRepository = UiConfigurationCoreInjector.singleton()
                .provideUiConfigurationRepository(),
            folderRepository = FoldersInjector.singleton().provideFoldersRepository(),
            ollamaRepository = ollamaInjection?.provideRepository(),
            keyboardEventFlow = keyboardEventFlow,
            copyManager = copyManager,
            workspaceConfigRepository = appConfigurationInjector.provideWorkspaceConfigRepository(),
            authRepository = authCoreInjection.provideAuthRepository()
        )

    @Composable
    override fun providePresentationViewModel(): PresentationViewModel = viewModel {
        PresentationKmpViewModel(documentRepository = provideDocumentRepository())
    }

    @Composable
    override fun provideNoteDetailsViewModel(
        parentFolder: String,
        copyManager: CopyManager
    ): NoteEditorViewModel =
        viewModel {
            provideNoteEditorViewModel(parentFolder = parentFolder, copyManager = copyManager)
        }

    companion object {
        fun mobile(
            daosInjection: RepositoryInjector,
            connectionInjector: WriteopiaConnectionInjector =
                WriteopiaConnectionInjector.singleton(),
            authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
        ) = EditorKmpInjector(
            authCoreInjection,
            daosInjection,
            connectionInjector,
            MutableStateFlow(false),
            MutableStateFlow(KeyboardEvent.IDLE),
        )

        fun desktop(
            authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
            repositoryInjection: RepositoryInjector = SqlDelightDaoInjector.singleton(),
            connectionInjection: WriteopiaConnectionInjector =
                WriteopiaConnectionInjector.singleton(),
            selectionState: StateFlow<Boolean>,
            keyboardEventFlow: Flow<KeyboardEvent>,
            ollamaInjection: OllamaInjection = OllamaInjection.singleton(),
        ) = EditorKmpInjector(
            authCoreInjection,
            repositoryInjection,
            connectionInjection,
            selectionState,
            keyboardEventFlow,
            ollamaInjection = ollamaInjection,
        )
    }
}
