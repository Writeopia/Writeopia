package io.writeopia.editor.features.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import io.writeopia.editor.features.editor.ui.desktop.DesktopNoteEditorScreen
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.ui.drawer.factory.DefaultDrawersJs

@Composable
actual fun TextEditorScreen(
    documentId: String?,
    title: String?,
    noteEditorViewModel: NoteEditorViewModel,
    isUndoKeyEvent: (KeyEvent) -> Boolean,
    navigateBack: () -> Unit,
    playPresentation: () -> Unit,
    onDocumentLinkClick: (String) -> Unit,
    modifier: Modifier,
) {
    DesktopNoteEditorScreen(
        documentId = documentId,
        noteEditorViewModel = noteEditorViewModel,
        drawersFactory = DefaultDrawersJs,
        isUndoKeyEvent = isUndoKeyEvent,
        onPresentationClick = playPresentation,
        onDocumentLinkClick = onDocumentLinkClick,
        modifier = modifier
    )
}
