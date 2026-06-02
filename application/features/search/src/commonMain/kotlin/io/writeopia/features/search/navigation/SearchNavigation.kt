package io.writeopia.features.search.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    nestedScrollConnection: NestedScrollConnection? = null,
    isToolbarVisible: Boolean = true,
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
                    AnimatedVisibility(
                        visible = isToolbarVisible,
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it }
                    ) {
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
                    }
                },
                bottomBar = navigationBar
            ) { paddingValues ->
                // Add extra bottom padding when navigation bar is visible
                val contentBottomPadding by animateDpAsState(
                    targetValue = if (isToolbarVisible) 96.dp else 0.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "contentBottomPadding"
                )
                val adjustedPaddingValues = PaddingValues(
                    start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = paddingValues.calculateBottomPadding() + contentBottomPadding
                )

                val scrollModifier = if (nestedScrollConnection != null) {
                    Modifier.padding(adjustedPaddingValues).nestedScroll(nestedScrollConnection)
                } else {
                    Modifier.padding(adjustedPaddingValues)
                }
                screen(scrollModifier)
            }
        } else {
            screen(Modifier)
        }
    }
}
