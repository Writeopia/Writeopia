package io.writeopia.features.search.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.writeopia.commonui.ScrollAwareTopBar
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
    navigateToPresentation: (documentId: String, presentationId: String, title: String) -> Unit,
    navigateToDocumentWithSearch: (documentId: String, title: String, query: String) -> Unit,
    navigationClick: () -> Unit,
    nestedScrollConnection: NestedScrollConnection? = null,
    isToolbarVisible: Boolean = true,
    navigationBar: @Composable () -> Unit,
) {
    composable(
        route = SearchDestiny.search(),
    ) { _ ->
        val viewModel = viewModel { searchInjection.provideViewModel() }

        // While typing in the search field the top bar steps aside to give the results more room.
        var isSearchFocused by remember { mutableStateOf(false) }

        val screen = @Composable { modifier: Modifier ->
            DocumentsSearchScreen(
                modifier = modifier,
                searchState = viewModel.searchState,
                searchResults = viewModel.queryResults,
                onSearchType = viewModel::onSearchType,
                documentClick = navigateToDocument,
                onFolderClick = navigateToFolder,
                presentationClick = navigateToPresentation,
                documentContentClick = navigateToDocumentWithSearch,
                onSearchFocusChange = { focused -> isSearchFocused = focused },
            )
        }

        val currentPlatform = LocalPlatform.current

        if (currentPlatform.isMobile()) {
            ClearFocusWhenKeyboardCloses(isSearchFocused)

            Scaffold(
                contentWindowInsets = WindowInsets.systemBars,
                topBar = {
                    val barColor = TopAppBarDefaults.topAppBarColors().containerColor

                    ScrollAwareTopBar(visible = isToolbarVisible) {
                        // The status bar strip stays put and only the bar under it folds away, so
                        // the search field moves up in one smooth step instead of slipping under
                        // the status bar and jumping back when the animation ends.
                        Column(modifier = Modifier.fillMaxWidth().background(barColor)) {
                            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                            AnimatedVisibility(
                                visible = !isSearchFocused,
                                enter = expandVertically(tween(TOP_BAR_ANIMATION_MS)) +
                                    fadeIn(tween(TOP_BAR_ANIMATION_MS)),
                                exit = shrinkVertically(tween(TOP_BAR_ANIMATION_MS)) +
                                    fadeOut(tween(TOP_BAR_ANIMATION_MS)),
                            ) {
                                TopAppBar(
                                    windowInsets = WindowInsets(0, 0, 0, 0),
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
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                )
                            }
                        }
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

private const val TOP_BAR_ANIMATION_MS = 250

/**
 * Closing the keyboard (the back gesture on Android, for instance) also leaves the search field,
 * so the top bar, and its way back, shows again.
 */
@Composable
private fun ClearFocusWhenKeyboardCloses(isSearchFocused: Boolean) {
    val focusManager = LocalFocusManager.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var wasKeyboardOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isKeyboardOpen) {
        if (wasKeyboardOpen && !isKeyboardOpen && isSearchFocused) {
            focusManager.clearFocus()
        }
        wasKeyboardOpen = isKeyboardOpen
    }
}
