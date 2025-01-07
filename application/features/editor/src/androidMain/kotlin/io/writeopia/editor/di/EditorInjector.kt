package io.writeopia.editor.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjection
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.presentation.viewmodel.PresentationViewModel
import io.writeopia.repository.UiConfigurationRepository
import io.writeopia.sdk.network.injector.ConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.flow.MutableStateFlow

class EditorInjector internal constructor(
    private val editorKmpInjector: EditorKmpInjector
) : TextEditorInjector {

    @Composable
    override fun provideNoteDetailsViewModel(parentFolderId: String): NoteEditorViewModel =
        viewModel {
            editorKmpInjector.provideNoteEditorViewModel(parentFolder = parentFolderId)
        }

    @Composable
    override fun providePresentationViewModel(): PresentationViewModel =
        editorKmpInjector.providePresentationViewModel()

    companion object {
        fun create(
            authCoreInjection: AuthCoreInjection,
            daosInjection: RepositoryInjector,
            connectionInjector: ConnectionInjector,
            uiConfigurationRepository: UiConfigurationRepository
        ) = EditorInjector(
            EditorKmpInjector(
                authCoreInjection,
                daosInjection,
                connectionInjector,
                MutableStateFlow(false),
                MutableStateFlow(KeyboardEvent.IDLE),
                uiConfigurationRepository
            )
        )
    }
}
