package io.writeopia.editor.features.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.sdk.models.story.StoryStep

@Composable
actual fun TextEditorScreen(
    documentId: String?,
    title: String?,
    isDarkTheme: Boolean,
    noteEditorViewModel: NoteEditorViewModel,
    navigateBack: () -> Unit,
    playPresentation: () -> Unit,
    onDocumentLinkClick: (String) -> Unit,
    onNewDrawingClick: () -> Unit,
    onDrawingClick: (StoryStep, Int) -> Unit,
    modifier: Modifier
) {
    NoteEditorScreen(
        isDarkTheme = isDarkTheme,
        documentId = documentId,
        title = title,
        noteEditorViewModel = noteEditorViewModel,
        navigateBack = navigateBack,
        onDocumentLinkClick = onDocumentLinkClick,
        onNewDrawingClick = onNewDrawingClick,
        onDrawingClick = onDrawingClick,
        modifier = modifier
    )
}
