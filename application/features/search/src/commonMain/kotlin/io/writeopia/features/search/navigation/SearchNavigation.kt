package io.writeopia.features.search.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.common.utils.Destinations
import io.writeopia.features.search.DocumentsSearchScreen
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.common.utils.NotesNavigation

object SearchDestiny {
    fun search() = Destinations.SEARCH.id
}

fun NavGraphBuilder.searchNavigation(
    isMobile: Boolean,
    searchInjection: SearchInjection,
    navigateToDocument: (String, String) -> Unit,
    navigateToFolder: (NotesNavigation) -> Unit,
) {
    composable(
        route = SearchDestiny.search(),
    ) { _ ->
        val viewModel = viewModel { searchInjection.provideViewModel() }

        val screen = @Composable { modifier: Modifier ->
            DocumentsSearchScreen(
                modifier = modifier,
                searchState = viewModel.searchState,
                searchResults = viewModel.queryResults,
                onSearchType = viewModel::onSearchType,
                documentClick = navigateToDocument,
                onFolderClick = navigateToFolder,
            )
        }

        if (isMobile) {
            Scaffold(
                contentWindowInsets = WindowInsets.systemBars,
            ) { paddingValues ->
                screen(Modifier.padding(paddingValues))
            }
        } else {
            screen(Modifier)
        }
    }
}
