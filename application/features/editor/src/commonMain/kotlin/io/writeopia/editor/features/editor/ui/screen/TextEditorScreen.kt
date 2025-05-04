package io.writeopia.editor.features.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel

@Composable
expect fun TextEditorScreen(
    documentId: String?,
    title: String?,
    noteEditorViewModel: NoteEditorViewModel,
    navigateBack: () -> Unit,
    playPresentation: () -> Unit,
    onDocumentLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
)
