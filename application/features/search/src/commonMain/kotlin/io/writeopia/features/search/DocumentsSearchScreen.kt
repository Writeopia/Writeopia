package io.writeopia.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import io.writeopia.features.search.repository.SearchItem
import io.writeopia.features.search.ui.SearchInput
import io.writeopia.features.search.ui.SearchScreen
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DocumentsSearchScreen(
    modifier: Modifier = Modifier,
    searchState: StateFlow<String>,
    searchResults: StateFlow<List<SearchItem>>,
    onSearchType: (String) -> Unit,
    documentClick: (String, String) -> Unit,
    onFolderClick: (NotesNavigation) -> Unit,
) {
    val search by searchState.collectAsState()

    SearchScreen(
        modifier = modifier.fillMaxSize(),
        search,
        searchResults,
        documentClick,
        onFolderClick,
        searchInput = {
            SearchInput(
                search,
                onSearchType,
                Modifier.padding(12.dp)
                    .shadow(
                        10.dp,
                        shape = MaterialTheme.shapes.large,
                        spotColor = WriteopiaTheme.colorScheme.cardShadow
                    )
                    .background(
                        WriteopiaTheme.colorScheme.searchBackground,
                        shape = MaterialTheme.shapes.large
                    )
            )
        }
    )
}
