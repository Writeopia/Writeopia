package io.writeopia.editor.features.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
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
    onDrawingClick: (StoryStep, Double) -> Unit = { _, _ -> },
    nestedScrollConnection: NestedScrollConnection? = null,
    isToolbarVisible: Boolean = true,
    modifier: Modifier = Modifier,
)
