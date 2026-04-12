package io.writeopia.editor.features.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.sdk.models.story.StoryStep

@Composable
expect fun TextEditorScreen(
    documentId: String?,
    title: String?,
    isDarkTheme: Boolean,
    noteEditorViewModel: NoteEditorViewModel,
    navigateBack: () -> Unit,
    playPresentation: () -> Unit,
    onDocumentLinkClick: (String) -> Unit,
    onNewDrawingClick: () -> Unit = {},
    onDrawingClick: (StoryStep, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
)
