package io.writeopia.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.currentBackStackEntryAsState
import io.writeopia.account.di.AccountMenuInjector
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.di.TextEditorInjector
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.model.isDarkTheme
import io.writeopia.navigation.NavItemName
import io.writeopia.navigation.Navigation
import io.writeopia.navigation.NavigationViewModel
import io.writeopia.navigation.notes.navigateToNoteMenu
import io.writeopia.navigation.notifications.navigateToNotifications
import io.writeopia.navigation.search.navigateToSearch
import io.writeopia.notemenu.data.model.NotesNavigation
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.theme.WrieopiaTheme
import io.writeopia.viewmodel.UiConfigurationViewModel

@Composable
fun AppMobile(
    startDestination: String = Destinations.AUTH_MENU_INNER_NAVIGATION.id,
    navController: NavHostController,
    searchInjector: SearchInjection,
    uiConfigViewModel: UiConfigurationViewModel,
    notesMenuInjection: NotesMenuInjection,
    editorInjector: TextEditorInjector,
    accountMenuInjector: AccountMenuInjector,
    navigationViewModel: NavigationViewModel,
    builder: NavGraphBuilder.() -> Unit
) {
    val colorTheme by uiConfigViewModel.listenForColorTheme { "disconnected_user" }.collectAsState()

    WrieopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    val navigationItems by navigationViewModel.selectedNavigation.collectAsState()

                    navigationItems.forEach { item ->
                        val isSelected =
                            currentDestination?.hierarchy?.any { destination ->
                                destination.route?.let {
                                    NavItemName.selectRoute(it)
                                }?.value == item.navItemName.value
                            } ?: false

                        NavigationBarItem(
                            selected = isSelected,
                            icon = {
                                Icon(
                                    imageVector = item.navItemName.iconForNavItem(),
                                    contentDescription = item.navItemName.value
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
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedIndicatorColor = MaterialTheme.colorScheme.secondary,
                                )
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                Navigation(
                    startDestination = startDestination,
                    notesMenuInjection = notesMenuInjection,
                    navController = navController,
                    editorInjector = editorInjector,
                    accountMenuInjector = accountMenuInjector,
                    selectColorTheme = uiConfigViewModel::changeColorTheme,
                    searchInjection = searchInjector,
                    isUndoKeyEvent = { false },
                    builder = builder
                )
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
