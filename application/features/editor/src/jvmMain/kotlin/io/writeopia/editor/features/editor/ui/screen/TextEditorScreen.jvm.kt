package io.writeopia.editor.features.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import io.writeopia.editor.features.editor.ui.desktop.DesktopNoteEditorScreen
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.presentation.ui.PresentationWindow
import io.writeopia.editor.features.presentation.ui.PresentationsHost
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.factory.DefaultDrawersDesktop

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
    modifier: Modifier,
) {
    // The presentations of the document open in windows of their own, like on the Mac. The
    // controller is null when nothing can make them (no cloud, no local model).
    val presentations = noteEditorViewModel.presentations
    val showPresentation = presentations?.let { it.isAvailable.collectAsState().value } ?: false
    var openPresentations by remember { mutableStateOf<List<Presentation>>(emptyList()) }

    // Desktop always shows SideEditorOptions regardless of window shape, so isWideLayout is unused here.
    DesktopNoteEditorScreen(
        isDarkTheme = isDarkTheme,
        documentId = documentId,
        noteEditorViewModel = noteEditorViewModel,
        drawersFactory = DefaultDrawersDesktop,
        onPresentationClick = { presentations?.openDialog() ?: playPresentation() },
        showPresentation = showPresentation,
        onDocumentLinkClick = onDocumentLinkClick,
        onDrawingClick = onDrawingClick,
        onNewDrawingClick = onNewDrawingClick,
        onDocumentDelete = navigateBack,
        modifier = modifier
    )

    if (presentations != null) {
        PresentationsHost(presentations) { presentation ->
            openPresentations = openPresentations.filter { it.id != presentation.id } + presentation
        }

        openPresentations.forEach { presentation ->
            key(presentation.id) {
                PresentationWindow(presentation = presentation, isDarkTheme = isDarkTheme) {
                    openPresentations = openPresentations.filter { it.id != presentation.id }
                }
            }
        }
    }
}
