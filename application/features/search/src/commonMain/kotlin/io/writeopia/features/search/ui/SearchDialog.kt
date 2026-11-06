package io.writeopia.features.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.features.search.repository.SearchItem
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SearchDialog(
    modifier: Modifier = Modifier,
    searchState: StateFlow<String>,
    searchResults: StateFlow<List<SearchItem>>,
    onSearchType: (String) -> Unit,
    onDismissRequest: () -> Unit,
    documentClick: (String, String) -> Unit,
    onFolderClick: (NotesNavigation) -> Unit,
    presentationClick: (documentId: String, presentationId: String, title: String) -> Unit,
    documentContentClick: (documentId: String, title: String, query: String) -> Unit = { id, title, _ ->
        documentClick(id, title)
    },
) {
    val search by searchState.collectAsState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = modifier.width(750.dp).height(500.dp), shape = MaterialTheme.shapes.large) {
            SearchScreen(
                searchResults = searchResults,
                documentClick = { id, title ->
                    documentClick(id, title)
                    onDismissRequest()
                },
                onFolderClick = { navigation ->
                    onFolderClick(navigation)
                    onDismissRequest()
                },
                presentationClick = { documentId, presentationId, title ->
                    presentationClick(documentId, presentationId, title)
                    onDismissRequest()
                },
                documentContentClick = { documentId, title, query ->
                    documentContentClick(documentId, title, query)
                    onDismissRequest()
                },
                search = search,
                searchInput = {
                    // Opened by Cmd/Ctrl+F or the side menu: the words go straight in.
                    SearchInput(search, onSearchType, requestFocus = true)
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
                }
            )
        }
    }
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    search: String,
    searchResults: StateFlow<List<SearchItem>>,
    documentClick: (String, String) -> Unit,
    onFolderClick: (NotesNavigation) -> Unit,
    presentationClick: (documentId: String, presentationId: String, title: String) -> Unit,
    searchInput: @Composable () -> Unit,
    /** A document found by the words inside it: opened with the search for [search] active. */
    documentContentClick: (documentId: String, title: String, query: String) -> Unit = { id, title, _ ->
        documentClick(id, title)
    },
) {
    Column(modifier = modifier) {
        searchInput()

        Spacer(modifier = Modifier.height(10.dp))

        val results by searchResults.collectAsState()

        val horizontalPadding = 4.dp
        val innerHorizontalPadding = 12.dp
        val innerVerticalPadding = 6.dp
        val spaceWidth = 8.dp
        val iconTint = WriteopiaTheme.colorScheme.tintLight

        if (search.isEmpty()) {
            Text(
                text = WrStrings.recent(),
                color = WriteopiaTheme.colorScheme.textLighter,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 10.dp),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        results.forEach { item ->
            when (item) {
                is SearchItem.DocumentInfo -> {
                    val snippet = item.snippet
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                if (snippet != null) {
                                    documentContentClick(item.id, item.label, search)
                                } else {
                                    documentClick(item.id, item.label)
                                }
                            }
                            .padding(
                                horizontal = innerHorizontalPadding,
                                vertical = innerVerticalPadding
                            )
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = WrIcons.file,
                            contentDescription = "File",
                            tint = iconTint
                        )

                        Spacer(modifier = Modifier.width(spaceWidth))

                        Column {
                            Text(
                                item.label,
                                color = iconTint
                            )

                            if (snippet != null) {
                                // The step the words were found in, the words marked.
                                Text(
                                    highlightedSnippet(
                                        text = SearchSnippet.window(snippet, search),
                                        query = search,
                                        highlight = MaterialTheme.colorScheme.onBackground
                                    ),
                                    color = WriteopiaTheme.colorScheme.textLighter,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                is SearchItem.PresentationInfo -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                presentationClick(item.documentId, item.id, item.label)
                            }
                            .padding(
                                horizontal = innerHorizontalPadding,
                                vertical = innerVerticalPadding
                            )
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = WrIcons.play,
                            contentDescription = "Presentation",
                            tint = iconTint
                        )

                        Spacer(modifier = Modifier.width(spaceWidth))

                        Text(
                            item.label,
                            color = iconTint
                        )
                    }
                }

                is SearchItem.FolderInfo -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                onFolderClick(NotesNavigation.Folder(item.id))
                            }
                            .padding(
                                horizontal = innerHorizontalPadding,
                                vertical = innerVerticalPadding
                            )
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = WrIcons.folder,
                            contentDescription = "File",
                            tint = iconTint
                        )

                        Spacer(modifier = Modifier.width(spaceWidth))

                        Text(
                            item.label,
                            color = iconTint,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
fun SearchInput(
    search: String,
    onSearchType: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Puts the cursor in the field as it shows. */
    requestFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    Row(
        modifier = modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = WrIcons.search,
            contentDescription = WrStrings.search(),
            tint = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(modifier = Modifier.weight(1F)) {
            BasicTextField(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center).focusRequester(focusRequester),
                value = search,
                onValueChange = onSearchType,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground)
            )

            if (search.isEmpty()) {
                Text(WrStrings.search())
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        AnimatedVisibility(
            search.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Icon(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        onSearchType("")
                    },
                imageVector = WrIcons.close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
