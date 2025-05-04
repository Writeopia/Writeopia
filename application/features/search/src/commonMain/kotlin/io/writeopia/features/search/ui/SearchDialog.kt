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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
                search = search,
                searchInput = {
                    SearchInput(search, onSearchType)
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
    searchInput: @Composable () -> Unit
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                documentClick(item.id, item.label)
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
fun SearchInput(search: String, onSearchType: (String) -> Unit, modifier: Modifier = Modifier) {
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
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
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
