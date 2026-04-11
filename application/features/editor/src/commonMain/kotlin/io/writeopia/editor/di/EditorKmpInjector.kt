package io.writeopia.editor.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.configuration.di.UiConfigurationCoreInjector
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.core.folders.di.InDocumentSearchInjection
import io.writeopia.di.OllamaInjection
import io.writeopia.editor.features.editor.copy.CopyManager
import io.writeopia.editor.features.editor.viewmodel.NoteEditorKmpViewModel
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.presentation.viewmodel.PresentationKmpViewModel
import io.writeopia.editor.features.presentation.viewmodel.PresentationViewModel
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.sharededition.SharedEditionManager
import io.writeopia.ui.keyboard.KeyboardEvent
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EditorKmpInjector private constructor(
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val repositoryInjection: RepositoryInjector,
    private val connectionInjection: WriteopiaConnectionInjector,
    private val selectionState: StateFlow<Boolean>,
    private val keyboardEventFlow: Flow<KeyboardEvent>,
    private val appConfigurationInjector: AppConfigurationInjector =
        AppConfigurationInjector.singleton(),
    private val ollamaInjection: OllamaInjection? = null,
    private val inDocumentSearchInjection: InDocumentSearchInjection =
        InDocumentSearchInjection.singleton(),
) : TextEditorInjector {

    private fun provideDocumentRepository(): DocumentRepository =
        repositoryInjection.provideDocumentRepository()

    private fun provideWriteopiaManager(): WriteopiaManager = WriteopiaManager(
        aiClient = ollamaInjection?.provideRepository()
    )

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
            authRepository = authCoreInjection.provideAuthRepository(),
            inDocumentSearchRepository = inDocumentSearchInjection.provideInDocumentSearchRepo()
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun addDrawingToDocument(
        documentId: String,
        storyStepId: String,
        drawingData: DrawingData
    ) {
        scope.launch {
            val documentRepository = provideDocumentRepository()
            val json = Json.encodeToString(drawingData)

            // Load the document to get current content size
            val workspaceId = authCoreInjection.provideAuthRepository().getWorkspace()?.id ?: ""
            val document = documentRepository.loadDocumentById(documentId, workspaceId)

            val storyStep = if (storyStepId.isNotEmpty()) {
                // Update existing drawing
                StoryStep(
                    id = storyStepId,
                    type = StoryTypes.DRAWING.type,
                    text = json
                )
            } else {
                // Create new drawing
                StoryStep(
                    id = GenerateId.generate(),
                    type = StoryTypes.DRAWING.type,
                    text = json
                )
            }

            // Calculate position: end of document for new drawings
            val position = if (storyStepId.isNotEmpty()) {
                // For updates, find the existing position
                document?.content?.entries?.find { it.value.id == storyStepId }?.key ?: (document?.content?.size ?: 0)
            } else {
                // For new drawings, add at the end
                document?.content?.size ?: 0
            }

            documentRepository.saveStoryStep(storyStep, position, documentId)
        }
    }

    companion object {
        fun mobile(
            connectionInjector: WriteopiaConnectionInjector =
                WriteopiaConnectionInjector.singleton(),
            authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
        ) = EditorKmpInjector(
            authCoreInjection,
            RepositoryInjector.singleton(),
            connectionInjector,
            MutableStateFlow(false),
            MutableStateFlow(KeyboardEvent.IDLE),
        )

        fun desktop(
            authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
            repositoryInjection: RepositoryInjector = RepositoryInjector.singleton(),
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
