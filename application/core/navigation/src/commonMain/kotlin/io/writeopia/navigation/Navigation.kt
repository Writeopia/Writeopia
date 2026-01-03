package io.writeopia.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.writeopia.account.navigation.accountMenuNavigation
import io.writeopia.common.utils.Destinations
import io.writeopia.documents.graph.di.DocumentsGraphInjection
import io.writeopia.documents.graph.navigation.documentsGraphNavigation
import io.writeopia.documents.graph.navigation.navigateToForceGraph
import io.writeopia.editor.di.TextEditorInjector
import io.writeopia.editor.navigation.editorNavigation
import io.writeopia.features.notifications.navigation.notificationsNavigation
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.features.search.navigation.searchNavigation
import io.writeopia.global.shell.di.SideMenuKmpInjector
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
    isDarkTheme: Boolean,
    isMobile: Boolean,
    startDestination: String,
    navController: NavHostController = rememberNavController(),
    notesMenuInjection: NotesMenuInjection,
    documentsGraphInjection: DocumentsGraphInjection? = null,
    sideMenuKmpInjector: SideMenuKmpInjector? = null,
    editorInjector: TextEditorInjector,
    searchInjection: SearchInjection? = null,
    selectColorTheme: (ColorThemeOption) -> Unit,
    navigationBar: @Composable () -> Unit,
    builder: NavGraphBuilder.() -> Unit
) {
    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = startDestination) {
            notesMenuNavigation(
                isDarkTheme = isDarkTheme,
                notesMenuInjection = notesMenuInjection,
                ollamaConfigInjector = sideMenuKmpInjector,
                navigationController = navController,
                sharedTransitionScope = this@SharedTransitionLayout,
                selectColorTheme = selectColorTheme,
                navigateToNote = navController::navigateToNote,
                navigateToAccount = navController::navigateToAccount,
                navigateToNewNote = navController::navigateToNewNote,
                navigateToFolders = navController::navigateToFolder,
                navigateToForceGraph = navController::navigateToForceGraph,
                navigationBar = navigationBar
            )

            if (documentsGraphInjection != null) {
                documentsGraphNavigation(
                    documentsGraphInjection = documentsGraphInjection
                )
            }

            editorNavigation(
                isDarkTheme = isDarkTheme,
                navigateBack = navController::navigateUp,
                editorInjector = editorInjector,
                navigateToPresentation = navController::navigateToPresentation,
                sharedTransitionScope = this@SharedTransitionLayout,
                navigateToNote = { id ->
                    navController.navigateToNote(id, title = "")
                },
            )

            accountMenuNavigation(
                navigateToAuthMenu = {
                    navController.navigate(Destinations.AUTH_MENU_INNER_NAVIGATION.id)
                },
                navigationClick = navController::navigateUp,
                selectColorTheme = selectColorTheme
            )

            if (searchInjection != null) {
                searchNavigation(
                    isMobile = isMobile,
                    searchInjection,
                    navigateToDocument = navController::navigateToNote,
                    navigateToFolder = navController::navigateToFolder,
                    navigationClick = navController::navigateUp
                )
            }

            notificationsNavigation()

            builder()
        }
    }
}
