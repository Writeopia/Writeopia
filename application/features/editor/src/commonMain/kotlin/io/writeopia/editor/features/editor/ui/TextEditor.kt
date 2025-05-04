package io.writeopia.editor.features.editor.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import io.writeopia.editor.configuration.ui.DrawConfigFactory
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.model.Font
import io.writeopia.ui.WriteopiaEditor
import io.writeopia.ui.drawer.factory.DrawersFactory
import io.writeopia.ui.model.DrawStory
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun TextEditor(
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
            noteEditorViewModel.scrollToPosition.collectLatest {
                listState.animateScrollBy(70F)
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

    val clipShape = MaterialTheme.shapes.medium
    val isEditable by noteEditorViewModel.isEditable.collectAsState()

    WriteopiaEditor(
        modifier = modifier,
        editable = editable,
        listState = listState,
        keyFn = keyFn,
        drawers = drawersFactory.create(
            noteEditorViewModel.writeopiaManager,
            defaultBorder = clipShape,
            onHeaderClick = noteEditorViewModel::onHeaderClick,
            editable = isEditable,
            groupsBackgroundColor = Color.Transparent,
            drawConfig = DrawConfigFactory.getDrawConfig(),
            fontFamily = fontFamily,
            generateSection = noteEditorViewModel::aiSection,
            receiveExternalFile = noteEditorViewModel::receiveExternalFile,
            onDocumentLinkClick = onDocumentLinkClick,
        ),
        storyState = storyState,
    )
}
