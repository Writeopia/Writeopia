package io.writeopia.features.search.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.common.utils.Destinations
import io.writeopia.features.search.DocumentsSearchScreen
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings

object SearchDestiny {
    fun search() = Destinations.SEARCH.id
}

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.searchNavigation(
    searchInjection: SearchInjection,
    navigateToDocument: (String, String) -> Unit,
    navigateToFolder: (NotesNavigation) -> Unit,
    navigationClick: () -> Unit,
    navigationBar: @Composable () -> Unit,
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

        val currentPlatform = LocalPlatform.current

        if (currentPlatform.isMobile()) {
            Scaffold(
                contentWindowInsets = WindowInsets.systemBars,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                WrStrings.search(),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        navigationIcon = {
                            Row(
                                modifier = Modifier.fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = navigationClick)
                                        .padding(10.dp),
                                    imageVector = WrIcons.backArrowMobile,
                                    contentDescription = "",
//                    stringResource(R.string.back),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    )
                },
                bottomBar = navigationBar
            ) { paddingValues ->
                screen(Modifier.padding(paddingValues))
            }
        } else {
            screen(Modifier)
        }
    }
}
