package io.writeopia.notemenu.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.writeopia.common.utils.Destinations
import io.writeopia.di.OllamaConfigInjector
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
    ollamaConfigInjector: OllamaConfigInjector? = null,
    navigationController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    selectColorTheme: (ColorThemeOption) -> Unit,
    navigateToNote: (String, String) -> Unit,
    navigateToNewNote: () -> Unit,
    navigateToAccount: () -> Unit,
    navigateToForceGraph: () -> Unit,
    navigateToFolders: (NotesNavigation) -> Unit,
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
        val navigationType = backStackEntry.arguments?.getString(NAVIGATION_TYPE)
        val navigationPath = backStackEntry.arguments?.getString(NAVIGATION_PATH)
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
        val ollamaConfigController = ollamaConfigInjector?.provideOllamaConfigController()

        NotesMenuScreen(
            isDarkTheme = isDarkTheme,
            folderId = notesNavigation.id,
            chooseNoteViewModel = chooseNoteViewModel,
            ollamaConfigController = ollamaConfigController,
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
            modifier = Modifier.background(MaterialTheme.colorScheme.background)
        )
    }

    composable(route = Destinations.MAIN_APP.id) { backStackEntry ->
        val notesNavigation = NotesNavigation.Root

        val chooseNoteViewModel: ChooseNoteViewModel =
            notesMenuInjection.provideChooseNoteViewModel(notesNavigation = notesNavigation)
        val ollamaConfigController = ollamaConfigInjector?.provideOllamaConfigController()

        NotesMenuScreen(
            isDarkTheme = isDarkTheme,
            folderId = notesNavigation.id,
            chooseNoteViewModel = chooseNoteViewModel,
            ollamaConfigController = ollamaConfigController,
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
