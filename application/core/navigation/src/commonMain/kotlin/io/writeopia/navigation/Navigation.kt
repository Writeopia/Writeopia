package io.writeopia.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.writeopia.account.di.AccountMenuInjector
import io.writeopia.account.navigation.accountMenuNavigation
import io.writeopia.common.utils.Destinations
import io.writeopia.editor.di.TextEditorInjector
import io.writeopia.editor.navigation.editorNavigation
import io.writeopia.features.notifications.navigation.notificationsNavigation
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.features.search.navigation.searchNavigation
import io.writeopia.model.ColorThemeOption
import io.writeopia.navigation.notes.navigateToAccount
import io.writeopia.navigation.notes.navigateToFolder
import io.writeopia.navigation.notes.navigateToNewNote
import io.writeopia.navigation.notes.navigateToNote
import io.writeopia.navigation.presentation.navigateToPresentation
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.notemenu.navigation.notesMenuNavigation

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Navigation(
    startDestination: String = Destinations.AUTH_MENU_INNER_NAVIGATION.id,
    navController: NavHostController = rememberNavController(),
    notesMenuInjection: NotesMenuInjection,
    editorInjector: TextEditorInjector,
    accountMenuInjector: AccountMenuInjector,
    searchInjection: SearchInjection? = null,
    isUndoKeyEvent: (KeyEvent) -> Boolean,
    selectColorTheme: (ColorThemeOption) -> Unit,
    builder: NavGraphBuilder.() -> Unit
) {
    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = startDestination) {
            notesMenuNavigation(
                notesMenuInjection = notesMenuInjection,
                navigationController = navController,
                sharedTransitionScope = this@SharedTransitionLayout,
                selectColorTheme = selectColorTheme,
                navigateToNote = navController::navigateToNote,
                navigateToAccount = navController::navigateToAccount,
                navigateToNewNote = navController::navigateToNewNote,
                navigateToFolders = navController::navigateToFolder
            )

            editorNavigation(
                navigateBack = {
                    navController.navigateUp()
                },
                editorInjector = editorInjector,
                isUndoKeyEvent = isUndoKeyEvent,
                navigateToPresentation = navController::navigateToPresentation,
                sharedTransitionScope = this@SharedTransitionLayout,
                navigateToNote = { id ->
                    navController.navigateToNote(id, title = "")
                },
            )

            accountMenuNavigation(
                accountMenuInjector = accountMenuInjector,
                navigateToAuthMenu = { },
                selectColorTheme = selectColorTheme
            )

            if (searchInjection != null) {
                searchNavigation(
                    searchInjection,
                    navController::navigateToNote,
                    navController::navigateToFolder
                )
            }

            notificationsNavigation()

            builder()
        }
    }
}
