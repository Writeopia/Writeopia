package io.writeopia.editor.features.editor.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

/**
 * The search inside the open document on the phones and tablets: a bar anchored to the top of
 * the editor, as wide as the screen, with the words, how many steps hold them, previous and
 * next, and the close button at the right end. The desktop app has [InDocumentSearchBar], a
 * floating card, instead.
 */
@Composable
fun MobileInDocumentSearchBar(
    noteEditorViewModel: NoteEditorViewModel,
    modifier: Modifier = Modifier
) {
    val textState by noteEditorViewModel.searchText.collectAsState()
    val showSearch by noteEditorViewModel.showSearchState.collectAsState()
    val currentResultIndex by noteEditorViewModel.currentSearchIndexState.collectAsState()
    val totalResults by noteEditorViewModel.totalSearchResultsState.collectAsState()

    AnimatedVisibility(
        showSearch,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val focusRequester = remember { FocusRequester() }

        Column(modifier = Modifier.fillMaxWidth().background(WriteopiaTheme.colorScheme.cardBg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1F), contentAlignment = Alignment.CenterStart) {
                    if (textState.isEmpty()) {
                        Text(
                            text = WrStrings.search(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = WriteopiaTheme.colorScheme.textLighter
                        )
                    }

                    BasicTextField(
                        value = textState,
                        onValueChange = noteEditorViewModel::searchInDocument,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { noteEditorViewModel.nextSearchResult() }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    )
                }

                Text(
                    modifier = Modifier.padding(horizontal = 8.dp).width(72.dp),
                    text = if (totalResults == 0) {
                        WrStrings.searchNoResults()
                    } else {
                        "${currentResultIndex + 1}/$totalResults"
                    },
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = WriteopiaTheme.colorScheme.textLighter
                )

                BarButton(
                    icon = WrIcons.smallArrowUp,
                    contentDescription = "Previous result",
                    enabled = totalResults > 0,
                    onClick = noteEditorViewModel::previousSearchResult
                )

                BarButton(
                    icon = WrIcons.smallArrowDown,
                    contentDescription = "Next result",
                    enabled = totalResults > 0,
                    onClick = noteEditorViewModel::nextSearchResult
                )

                BarButton(
                    icon = WrIcons.close,
                    contentDescription = "Close",
                    onClick = noteEditorViewModel::hideSearch
                )
            }

            HorizontalDivider(color = WriteopiaTheme.colorScheme.dividerColor)
        }

        LaunchedEffect(key1 = Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun RowScope.BarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(BUTTON_SIZE)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )
    }
}

private val BAR_HEIGHT = 56.dp
private val BUTTON_SIZE = 44.dp
