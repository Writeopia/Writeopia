package io.writeopia.mobile

import androidx.compose.animation.SharedTransitionScope
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
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.ChooseNoteRoute
import io.writeopia.common.utils.MainAppRoute
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.NotificationsRoute
import io.writeopia.common.utils.Route
import io.writeopia.common.utils.SearchRoute
import io.writeopia.common.utils.StartAppRoute
import io.writeopia.common.utils.AuthMenuInnerNavigationRoute
import io.writeopia.common.utils.AuthMenuRoute
import io.writeopia.common.utils.AuthRegisterRoute
import io.writeopia.common.utils.AuthResetPasswordRoute
import io.writeopia.common.utils.ChooseWorkspaceRoute
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.rememberScrollAwareState
import io.writeopia.drawing.di.DrawingInjection
import io.writeopia.editor.di.TextEditorInjector
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.model.AccentColor
import io.writeopia.model.isDarkTheme
import io.writeopia.navigation.NavItemName
import io.writeopia.navigation.Navigation
import io.writeopia.navigation.NavigationViewModel
import io.writeopia.navigation.rememberWriteopiaNavBackStack
import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.viewmodel.UiConfigurationViewModel

@Composable
fun PortraitMobile(
    startDestination: Route,
    searchInjector: SearchInjection,
    uiConfigViewModel: UiConfigurationViewModel,
    notesMenuInjection: NotesMenuInjection,
    editorInjector: TextEditorInjector,
    navigationViewModel: NavigationViewModel,
    drawingInjection: DrawingInjection? = null,
    onDrawingSaved: (String, String, DrawingData) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    extraEntries: @Composable (NavBackStack<NavKey>, SharedTransitionScope) -> Unit = { _, _ -> }
) {
    val colorThemeState = uiConfigViewModel.listenForColorTheme { "disconnected_user" }
    val colorTheme by colorThemeState.collectAsState()
    val accentColorState = uiConfigViewModel.listenForAccentColor { "disconnected_user" }
    val accentColor by accentColorState.collectAsState()
    val scrollAwareState = rememberScrollAwareState()

    // Use Navigation 3's backStack
    val backStack: NavBackStack<NavKey> = rememberWriteopiaNavBackStack(startDestination)

    WriteopiaTheme(
        darkTheme = colorTheme.isDarkTheme(),
        accentColor = accentColor ?: AccentColor.PURPLE
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Navigation(
                isDarkTheme = colorTheme.isDarkTheme(),
                startDestination = startDestination,
                notesMenuInjection = notesMenuInjection,
                editorInjector = editorInjector,
                drawingInjection = drawingInjection,
                selectedColorTheme = colorThemeState,
                selectedAccentColor = accentColorState,
                selectColorTheme = uiConfigViewModel::changeColorTheme,
                selectAccentColor = uiConfigViewModel::changeAccentColor,
                onDrawingSaved = onDrawingSaved,
                searchInjection = searchInjector,
                nestedScrollConnection = scrollAwareState.nestedScrollConnection,
                isToolbarVisible = scrollAwareState.isVisible,
                navigationBar = {},
                externalBackStack = backStack,
                extraEntries = extraEntries
            )

            // Overlay navigation bar on top of content
            val currentRoute = backStack.lastOrNull()
            val navigationItems by navigationViewModel.selectedNavigation.collectAsState()

            // Hide navigation bar on auth screens
            val isAuthScreen = currentRoute?.let { route ->
                route is AuthMenuRoute ||
                    route is AuthMenuInnerNavigationRoute ||
                    route is AuthRegisterRoute ||
                    route is AuthResetPasswordRoute ||
                    route is ChooseWorkspaceRoute ||
                    route is StartAppRoute
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
                        val isSelected = isItemSelected(currentRoute, item.navItemName)

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
                                if (!isSelected) {
                                    backStack.navigateToItem(item.navItemName)
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

private fun isItemSelected(currentRoute: NavKey?, navItem: NavItemName): Boolean {
    return when (navItem) {
        NavItemName.HOME -> currentRoute is MainAppRoute || currentRoute is ChooseNoteRoute
        NavItemName.SEARCH -> currentRoute is SearchRoute
        NavItemName.NOTIFICATIONS -> currentRoute is NotificationsRoute
    }
}

fun NavBackStack<NavKey>.navigateToItem(navItem: NavItemName) {
    when (navItem) {
        NavItemName.HOME -> add(MainAppRoute)
        NavItemName.SEARCH -> add(SearchRoute)
        NavItemName.NOTIFICATIONS -> add(NotificationsRoute)
    }
}

fun NavItemName.iconForNavItem(): ImageVector =
    when (this) {
        NavItemName.HOME -> WrIcons.home
        NavItemName.SEARCH -> WrIcons.search
        NavItemName.NOTIFICATIONS -> WrIcons.notifications
    }
