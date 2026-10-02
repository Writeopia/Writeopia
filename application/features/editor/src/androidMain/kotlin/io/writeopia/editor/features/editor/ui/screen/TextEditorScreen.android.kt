package io.writeopia.editor.features.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.presentation.ui.PresentationFullScreen
import io.writeopia.editor.features.presentation.ui.PresentationsHost
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.factory.DefaultDrawersAndroid

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
    onNewImageClick: () -> Unit,
    onDrawingClick: (StoryStep, Double) -> Unit,
    nestedScrollConnection: NestedScrollConnection?,
    isToolbarVisible: Boolean,
    isWideLayout: Boolean,
    modifier: Modifier
) {
    // The presentations of the document, cloud AI only on the phones: the controller is null
    // when the workspace can't make them.
    val presentations = noteEditorViewModel.presentations
    val showPresentation = presentations?.let { it.isAvailable.collectAsState().value } ?: false
    var openPresentation by remember { mutableStateOf<Presentation?>(null) }

    NoteEditorScreen(
        isDarkTheme = isDarkTheme,
        documentId = documentId,
        title = title,
        noteEditorViewModel = noteEditorViewModel,
        navigateBack = navigateBack,
        onDocumentLinkClick = onDocumentLinkClick,
        onNewDrawingClick = onNewDrawingClick,
        onNewImageClick = onNewImageClick,
        onDrawingClick = onDrawingClick,
        nestedScrollConnection = nestedScrollConnection,
        isToolbarVisible = isToolbarVisible,
        isWideLayout = isWideLayout,
        onPresentationClick = { presentations?.openDialog() ?: playPresentation() },
        showPresentation = showPresentation,
        onDocumentDelete = navigateBack,
        modifier = modifier
    )

    if (presentations != null) {
        PresentationsHost(presentations) { presentation -> openPresentation = presentation }

        openPresentation?.let { presentation ->
            PresentationFullScreen(
                presentation = presentation,
                isDarkTheme = isDarkTheme,
                drawersFactory = DefaultDrawersAndroid,
                onClose = { openPresentation = null }
            )
        }
    }
}
