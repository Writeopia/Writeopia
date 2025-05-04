package io.writeopia.editor.features.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel

@Composable
actual fun TextEditorScreen(
    documentId: String?,
    title: String?,
    noteEditorViewModel: NoteEditorViewModel,
    navigateBack: () -> Unit,
    playPresentation: () -> Unit,
    onDocumentLinkClick: (String) -> Unit,
    modifier: Modifier
) {
    NoteEditorScreen(
        documentId = documentId,
        title = title,
        noteEditorViewModel = noteEditorViewModel,
        navigateBack = navigateBack,
        onDocumentLinkClick = onDocumentLinkClick,
        modifier = modifier
    )
}
