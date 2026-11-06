package io.writeopia.editor.features.editor.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.isEnterKey

/**
 * The search inside the open document: the words, how many steps hold them, previous and next,
 * and a close button. Shown while the view model's search is open (Cmd/Ctrl+F on desktop, or a
 * document opened from the global search by the words inside it).
 */
@Composable
fun InDocumentSearchBar(noteEditorViewModel: NoteEditorViewModel, modifier: Modifier = Modifier) {
    val textState by noteEditorViewModel.searchText.collectAsState()
    val showSearch by noteEditorViewModel.showSearchState.collectAsState()
    val currentResultIndex by noteEditorViewModel.currentSearchIndexState.collectAsState()
    val totalResults by noteEditorViewModel.totalSearchResultsState.collectAsState()
    val shape = MaterialTheme.shapes.medium

    AnimatedVisibility(
        showSearch,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 150)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150))
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            val focusRequester = remember { FocusRequester() }

            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .background(WriteopiaTheme.colorScheme.cardBg, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = textState,
                    onValueChange = noteEditorViewModel::searchInDocument,
                    modifier = Modifier.defaultMinSize(minWidth = 160.dp)
                        .focusRequester(focusRequester)
                        .padding(8.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp &&
                                keyEvent.key.isEnterKey()
                            ) {
                                noteEditorViewModel.nextSearchResult()
                                true
                            }

                            false
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                )

                Text(
                    modifier = Modifier.padding(4.dp).width(100.dp),
                    text = if (totalResults == 0) WrStrings.searchNoResults() else "${currentResultIndex + 1}/$totalResults",
                    textAlign = TextAlign.Right,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Icon(
                    imageVector = WrIcons.smallArrowUp,
                    contentDescription = "Previous result",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = noteEditorViewModel::previousSearchResult)
                        .padding(4.dp)
                )

                Icon(
                    imageVector = WrIcons.smallArrowDown,
                    contentDescription = "Next result",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = noteEditorViewModel::nextSearchResult)
                        .padding(4.dp)
                )
            }

            Icon(
                imageVector = WrIcons.close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = noteEditorViewModel::hideSearch)
                    .background(WriteopiaTheme.colorScheme.cardBg, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(4.dp)
            )

            LaunchedEffect(key1 = Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}
