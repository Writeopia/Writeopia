package io.writeopia.mobile

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.currentBackStackEntryAsState
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.rememberScrollAwareState
import io.writeopia.drawing.di.DrawingInjection
import io.writeopia.editor.di.TextEditorInjector
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.model.isDarkTheme
import io.writeopia.navigation.NavItemName
import io.writeopia.navigation.Navigation
import io.writeopia.navigation.NavigationViewModel
import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.navigation.notes.navigateToNoteMenu
import io.writeopia.navigation.notifications.navigateToNotifications
import io.writeopia.navigation.search.navigateToSearch
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.viewmodel.UiConfigurationViewModel

@Composable
fun PortraitMobile(
    startDestination: String,
    navController: NavHostController,
    searchInjector: SearchInjection,
    uiConfigViewModel: UiConfigurationViewModel,
    notesMenuInjection: NotesMenuInjection,
    editorInjector: TextEditorInjector,
    navigationViewModel: NavigationViewModel,
    drawingInjection: DrawingInjection? = null,
    onDrawingSaved: (String, String, DrawingData) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit
) {
    val colorTheme by uiConfigViewModel.listenForColorTheme { "disconnected_user" }.collectAsState()
    val scrollAwareState = rememberScrollAwareState()

    WriteopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
        Box(modifier = modifier.fillMaxSize()) {
            Navigation(
                isDarkTheme = colorTheme.isDarkTheme(),
                startDestination = startDestination,
                notesMenuInjection = notesMenuInjection,
                navController = navController,
                editorInjector = editorInjector,
                drawingInjection = drawingInjection,
                selectColorTheme = uiConfigViewModel::changeColorTheme,
                onDrawingSaved = onDrawingSaved,
                searchInjection = searchInjector,
                nestedScrollConnection = scrollAwareState.nestedScrollConnection,
                isToolbarVisible = scrollAwareState.isVisible,
                navigationBar = {},
                builder = builder
            )

            // Overlay navigation bar on top of content
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val navigationItems by navigationViewModel.selectedNavigation.collectAsState()

            // Hide navigation bar on auth screens
            val currentRoute = currentDestination?.route
            val isAuthScreen = currentRoute?.let { route ->
                route.startsWith(Destinations.AUTH_MENU.id) ||
                    route.startsWith(Destinations.AUTH_MENU_INNER_NAVIGATION.id) ||
                    route.startsWith(Destinations.AUTH_REGISTER.id) ||
                    route.startsWith(Destinations.AUTH_RESET_PASSWORD.id) ||
                    route.startsWith(Destinations.AUTH_LOGIN.id) ||
                    route.startsWith(Destinations.CHOOSE_WORKSPACE.id) ||
                    route.startsWith(Destinations.START_APP.id)
            } ?: true

            val offsetY by animateDpAsState(
                targetValue = if (scrollAwareState.isVisible && !isAuthScreen) 0.dp else 100.dp,
                animationSpec = tween(durationMillis = 300),
                label = "navBarOffset"
            )

            if (!isAuthScreen) {
                NavigationBar(
                    containerColor = WriteopiaTheme.colorScheme.lightBackground,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(0, offsetY.roundToPx()) }
                ) {
                    navigationItems.forEach { item ->
                        val isSelected =
                            currentDestination?.hierarchy?.any { destination ->
                                destination.route?.let {
                                    NavItemName.selectRoute(it)
                                }?.value == item.navItemName.value
                            } ?: false

                        val iconSize by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 24.dp,
                            animationSpec = tween(durationMillis = 200),
                            label = "iconSize"
                        )

                        NavigationBarItem(
                            selected = isSelected,
                            icon = {
                                Icon(
                                    imageVector = item.navItemName.iconForNavItem(),
                                    contentDescription = item.navItemName.value,
                                    modifier = Modifier.size(iconSize)
                                )
                            },
                            onClick = {
                                navController.navigateToItem(item.navItemName) {
                                    if (!isSelected) {
                                        popUpTo(navController.graph.findStartDestination().route!!) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors()
                                .copy(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                                    selectedIndicatorColor = Color.Transparent,
                                )
                        )
                    }
                }
            }
        }
    }
}

fun NavHostController.navigateToItem(
    navItem: NavItemName,
    builder: NavOptionsBuilder.() -> Unit
) {
    when (navItem) {
        NavItemName.HOME -> this.navigateToNoteMenu(NotesNavigation.Root, builder)
        NavItemName.SEARCH -> this.navigateToSearch(builder)
        NavItemName.NOTIFICATIONS -> this.navigateToNotifications(builder)
    }
}

fun NavItemName.iconForNavItem(): ImageVector =
    when (this) {
        NavItemName.HOME -> WrIcons.home
        NavItemName.SEARCH -> WrIcons.search
        NavItemName.NOTIFICATIONS -> WrIcons.notifications
    }
