package io.writeopia.notemenu.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.writeopia.common.utils.Destinations
import io.writeopia.di.LocalAiConfigInjector
import io.writeopia.model.ColorThemeOption
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.NotesNavigationType
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.notemenu.ui.screen.menu.NotesMenuScreen
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel

const val NAVIGATION_TYPE = "type"
const val NAVIGATION_PATH = "path"

object NoteMenuDestiny {
    fun noteMenu() = "${Destinations.CHOOSE_NOTE.id}/{$NAVIGATION_TYPE}/{$NAVIGATION_PATH}"
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.notesMenuNavigation(
    isDarkTheme: Boolean,
    notesMenuInjection: NotesMenuInjection,
    localAiConfigInjector: LocalAiConfigInjector? = null,
    navigationController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    selectColorTheme: (ColorThemeOption) -> Unit,
    navigateToNote: (String, String) -> Unit,
    navigateToNewNote: () -> Unit,
    navigateToAccount: () -> Unit,
    navigateToForceGraph: () -> Unit,
    navigateToFolders: (NotesNavigation) -> Unit,
    nestedScrollConnection: NestedScrollConnection? = null,
    isToolbarVisible: Boolean = true,
    navigationBar: @Composable () -> Unit,
    isWideLayout: Boolean = false,
    sideMenuContent: @Composable () -> Unit = {},
) {
    composable(
        route = NoteMenuDestiny.noteMenu(),
        arguments = listOf(
            navArgument(NAVIGATION_TYPE) {
                type = NavType.StringType
                defaultValue = NotesNavigationType.ROOT.type
            },
            navArgument(NAVIGATION_PATH) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStackEntry ->
        val navigationType = backStackEntry.savedStateHandle.get<String?>(NAVIGATION_TYPE)
        val navigationPath = backStackEntry.savedStateHandle.get<String?>(NAVIGATION_PATH)
        val notesNavigation = if (navigationType != null && navigationPath != null) {
            NotesNavigation.fromType(
                NotesNavigationType.fromType(navigationType),
                navigationPath
            )
        } else {
            NotesNavigation.Root
        }

        val chooseNoteViewModel: ChooseNoteViewModel =
            notesMenuInjection.provideChooseNoteViewModel(notesNavigation = notesNavigation)
        val localAiConfigController = localAiConfigInjector?.provideLocalAiConfigController()

        NotesMenuScreen(
            isDarkTheme = isDarkTheme,
            folderId = notesNavigation.id,
            chooseNoteViewModel = chooseNoteViewModel,
            localAiConfigController = localAiConfigController,
            navigationController = navigationController,
            animatedVisibilityScope = this@composable,
            sharedTransitionScope = sharedTransitionScope,
            onNewNoteClick = navigateToNewNote,
            onNoteClick = navigateToNote,
            onAccountClick = navigateToAccount,
            selectColorTheme = selectColorTheme,
            navigateToFolders = navigateToFolders,
            addFolder = chooseNoteViewModel::addFolder,
            editFolder = chooseNoteViewModel::editFolder,
            onForceGraphSelected = navigateToForceGraph,
            nestedScrollConnection = nestedScrollConnection,
            isToolbarVisible = isToolbarVisible,
            navigationBar = navigationBar,
            isWideLayout = isWideLayout,
            sideMenuContent = sideMenuContent,
            modifier = Modifier.background(MaterialTheme.colorScheme.background)
        )
    }

    composable(route = Destinations.MAIN_APP.id) { backStackEntry ->
        val notesNavigation = NotesNavigation.Root

        val chooseNoteViewModel: ChooseNoteViewModel =
            notesMenuInjection.provideChooseNoteViewModel(notesNavigation = notesNavigation)
        val localAiConfigController = localAiConfigInjector?.provideLocalAiConfigController()

        NotesMenuScreen(
            isDarkTheme = isDarkTheme,
            folderId = notesNavigation.id,
            chooseNoteViewModel = chooseNoteViewModel,
            localAiConfigController = localAiConfigController,
            navigationController = navigationController,
            animatedVisibilityScope = this@composable,
            sharedTransitionScope = sharedTransitionScope,
            onNewNoteClick = navigateToNewNote,
            onNoteClick = navigateToNote,
            onAccountClick = navigateToAccount,
            selectColorTheme = selectColorTheme,
            navigateToFolders = navigateToFolders,
            addFolder = chooseNoteViewModel::addFolder,
            editFolder = chooseNoteViewModel::editFolder,
            onForceGraphSelected = navigateToForceGraph,
            nestedScrollConnection = nestedScrollConnection,
            isToolbarVisible = isToolbarVisible,
            navigationBar = navigationBar,
            isWideLayout = isWideLayout,
            sideMenuContent = sideMenuContent,
            modifier = Modifier.background(MaterialTheme.colorScheme.background)
        )
    }
}

fun NavController.navigateToNotes(navigation: NotesNavigation) {
    when (navigation) {
        is NotesNavigation.Folder -> {
            navigate(
                "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/${navigation.id}",
            )
        }

        NotesNavigation.Favorites, NotesNavigation.Root -> navigate(
            "${Destinations.CHOOSE_NOTE.id}/${navigation.navigationType.type}/path",
        )
    }
}

/**
 * Goes back to a folder of the breadcrumb (or the root), popping the screens above it when it's
 * already on the back stack, so the stack doesn't grow with each tap; opens it otherwise.
 */
fun NavController.navigateBackToNotes(navigation: NotesNavigation) {
    // Popping by route can't tell two folders apart: the route matching ignores the arguments
    // of the entries, so the first folder screen from the top always matches. The entry is
    // found by its arguments instead and everything above it is popped.
    @Suppress("RestrictedApi")
    val stack = currentBackStack.value
    val target = stack.lastOrNull { entry -> entry.shows(navigation) }

    when {
        target == null -> navigateToNotes(navigation)

        else -> repeat(stack.size - 1 - stack.indexOf(target)) { popBackStack() }
    }
}

private fun NavBackStackEntry.shows(navigation: NotesNavigation): Boolean =
    when (navigation) {
        is NotesNavigation.Folder ->
            destination.route == NoteMenuDestiny.noteMenu() &&
                savedStateHandle.get<String?>(NAVIGATION_TYPE) == navigation.navigationType.type &&
                savedStateHandle.get<String?>(NAVIGATION_PATH) == navigation.id

        NotesNavigation.Favorites ->
            destination.route == NoteMenuDestiny.noteMenu() &&
                savedStateHandle.get<String?>(NAVIGATION_TYPE) == navigation.navigationType.type

        NotesNavigation.Root -> destination.route == Destinations.MAIN_APP.id || showsRootNotes()
    }

private fun NavBackStackEntry.showsRootNotes(): Boolean =
    destination.route == NoteMenuDestiny.noteMenu() &&
        savedStateHandle.get<String?>(NAVIGATION_TYPE) == NotesNavigationType.ROOT.type
