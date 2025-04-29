package io.writeopia.features.search.navigation

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
    searchInjection: SearchInjection,
    navigateToDocument: (String, String) -> Unit,
    navigateToFolder: (NotesNavigation) -> Unit,
) {
    composable(
        route = SearchDestiny.search(),
    ) { _ ->
        val viewModel = viewModel { searchInjection.provideViewModel() }

        DocumentsSearchScreen(
            searchState = viewModel.searchState,
            searchResults = viewModel.queryResults,
            onSearchType = viewModel::onSearchType,
            documentClick = navigateToDocument,
            onFolderClick = navigateToFolder,
        )
    }
}
