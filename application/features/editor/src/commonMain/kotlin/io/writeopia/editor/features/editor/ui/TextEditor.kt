package io.writeopia.editor.features.editor.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import io.writeopia.editor.configuration.ui.DrawConfigFactory
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.model.Font
import io.writeopia.resources.WrStrings
import io.writeopia.ui.WriteopiaEditor
import io.writeopia.ui.drawer.factory.DrawersFactory
import io.writeopia.ui.model.DrawStory
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun TextEditor(
    isDarkTheme: Boolean,
    noteEditorViewModel: NoteEditorViewModel,
    drawersFactory: DrawersFactory,
    modifier: Modifier = Modifier,
    keyFn: (DrawStory) -> Int = { drawStory ->
        drawStory.desktopKey + (drawStory.cursor?.position ?: 0)
    },
    onDocumentLinkClick: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val storyState by noteEditorViewModel.toDrawWithDecoration.collectAsState()
    val editable by noteEditorViewModel.isEditable.collectAsState()
    val position by noteEditorViewModel.scrollToPosition.collectAsState()

    if (position != null) {
        LaunchedEffect(position, block = {
            noteEditorViewModel.scrollToPosition.collectLatest { position ->
                if (position == -1) {
                    listState.animateScrollBy(70F)
                } else if (position != null) {
                    listState.scrollToItem(position, scrollOffset = -100)
                }
            }
        })
    }

    val fontFamilyEnum by noteEditorViewModel.fontFamily.collectAsState()
    val fontFamily by remember {
        derivedStateOf {
            when (fontFamilyEnum) {
                Font.SYSTEM -> FontFamily.Default
                Font.SERIF -> FontFamily.Serif
                Font.MONOSPACE -> FontFamily.Monospace
                Font.CURSIVE -> FontFamily.Cursive
            }
        }
    }
    val isEditable by noteEditorViewModel.isEditable.collectAsState()

    WriteopiaEditor(
        modifier = modifier,
        editable = editable,
        listState = listState,
        keyFn = keyFn,
        drawers = drawersFactory.create(
            noteEditorViewModel.writeopiaManager,
            onHeaderClick = noteEditorViewModel::onHeaderClick,
            editable = isEditable,
            aiExplanation = WrStrings.aiExplanation(),
            isDarkTheme = isDarkTheme,
            drawConfig = DrawConfigFactory.getDrawConfig(),
            fontFamily = fontFamily,
            generateSection = noteEditorViewModel::aiSection,
            receiveExternalFile = noteEditorViewModel::receiveExternalFile,
            onDocumentLinkClick = onDocumentLinkClick,
            equationToImageUrl = "https://latex.codecogs.com/png.latex?\\Large&space;x="
        ),
        storyState = storyState,
    )
}
