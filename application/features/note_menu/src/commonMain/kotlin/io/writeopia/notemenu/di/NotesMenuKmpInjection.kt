package io.writeopia.notemenu.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjection
import io.writeopia.core.folders.repository.FolderRepository
import io.writeopia.notemenu.data.model.NotesNavigation
import io.writeopia.notemenu.data.repository.ConfigurationRepository
import io.writeopia.notemenu.data.usecase.NotesUseCase
import io.writeopia.notemenu.viewmodel.ChooseNoteKmpViewModel
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel
import io.writeopia.notemenu.viewmodel.FolderStateController
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotesMenuKmpInjection private constructor(
    private val notesInjector: NotesInjector,
    private val authCoreInjection: AuthCoreInjection,
    private val repositoryInjection: RepositoryInjector,
    private val selectionState: StateFlow<Boolean>,
    private val keyboardEventFlow: Flow<KeyboardEvent>
) : NotesMenuInjection {

    private fun provideDocumentRepository(): DocumentRepository =
        repositoryInjection.provideDocumentRepository()

    private fun provideNotesUseCase(
        documentRepository: DocumentRepository = provideDocumentRepository(),
        configurationRepository: ConfigurationRepository =
            notesInjector.provideNotesConfigurationRepository(),
        folderRepository: FolderRepository = notesInjector.provideFoldersRepository()
    ): NotesUseCase {
        return NotesUseCase.singleton(documentRepository, configurationRepository, folderRepository)
    }

    private fun provideFolderStateController(): FolderStateController =
        FolderStateController(
            provideNotesUseCase(),
            authCoreInjection.provideAccountManager()
        )

    internal fun provideChooseKmpNoteViewModel(
        notesNavigation: NotesNavigation,
        notesUseCase: NotesUseCase = provideNotesUseCase(),
        notesConfig: ConfigurationRepository =
            notesInjector.provideNotesConfigurationRepository(),
    ): ChooseNoteKmpViewModel =
        ChooseNoteKmpViewModel(
            notesUseCase = notesUseCase,
            notesConfig = notesConfig,
            authManager = authCoreInjection.provideAccountManager(),
            selectionState = selectionState,
            notesNavigation = notesNavigation,
            folderController = provideFolderStateController(),
            keyboardEventFlow = keyboardEventFlow
        )

    @Composable
    override fun provideChooseNoteViewModel(
        notesNavigation: NotesNavigation
    ): ChooseNoteViewModel =
        viewModel {
            provideChooseKmpNoteViewModel(notesNavigation)
        }

    companion object {
        fun mobile(
            notesInjector: NotesInjector,
            authCoreInjection: AuthCoreInjection,
            repositoryInjection: RepositoryInjector,
        ) = NotesMenuKmpInjection(
            notesInjector = notesInjector,
            authCoreInjection = authCoreInjection,
            repositoryInjection = repositoryInjection,
            selectionState = MutableStateFlow(false),
            keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE)
        )

        fun desktop(
            notesInjector: NotesInjector,
            authCoreInjection: AuthCoreInjection,
            repositoryInjection: RepositoryInjector,
            selectionState: StateFlow<Boolean>,
            keyboardEventFlow: Flow<KeyboardEvent>
        ) = NotesMenuKmpInjection(
            notesInjector = notesInjector,
            authCoreInjection = authCoreInjection,
            repositoryInjection = repositoryInjection,
            selectionState = selectionState,
            keyboardEventFlow = keyboardEventFlow,
        )
    }
}
