package io.writeopia.notemenu.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The breadcrumb goes back to a folder that is already on the back stack instead of opening it
 * again on top, so pressing back afterwards doesn't walk through the same folders twice.
 */
@OptIn(ExperimentalTestApi::class)
class NavigateBackToNotesTest {

    @Test
    fun `going back to an ancestor pops the folders above it`() = runComposeUiTest {
        val navController = startNavigation()

        runOnIdle {
            navController.navigateToNotes(NotesNavigation.Folder("a"))
            navController.navigateToNotes(NotesNavigation.Folder("b"))
            navController.navigateToNotes(NotesNavigation.Folder("c"))
        }
        runOnIdle { navController.navigateBackToNotes(NotesNavigation.Folder("a")) }

        runOnIdle {
            assertEquals("a", navController.currentFolderId())
            assertEquals(Destinations.MAIN_APP.id, navController.previousBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun `going back home pops everything down to the start of the app`() = runComposeUiTest {
        val navController = startNavigation()

        runOnIdle {
            navController.navigateToNotes(NotesNavigation.Folder("a"))
            navController.navigateToNotes(NotesNavigation.Folder("b"))
        }
        runOnIdle { navController.navigateBackToNotes(NotesNavigation.Root) }

        runOnIdle {
            assertEquals(Destinations.MAIN_APP.id, navController.currentBackStackEntry?.destination?.route)
            assertNull(navController.previousBackStackEntry)
        }
    }

    @Test
    fun `a folder that is not on the back stack is opened on top`() = runComposeUiTest {
        val navController = startNavigation()

        runOnIdle { navController.navigateToNotes(NotesNavigation.Folder("a")) }
        runOnIdle { navController.navigateBackToNotes(NotesNavigation.Folder("other")) }

        runOnIdle {
            assertEquals("other", navController.currentFolderId())
            assertEquals("a", navController.previousBackStackEntry?.folderId())
        }
    }

    /** A navigation graph with the routes of the notes menu, starting at the root like the app. */
    private fun androidx.compose.ui.test.ComposeUiTest.startNavigation(): NavHostController {
        lateinit var navController: NavHostController

        setContent {
            navController = rememberNavController()

            NavHost(navController = navController, startDestination = Destinations.MAIN_APP.id) {
                composable(route = Destinations.MAIN_APP.id) { Box {} }
                composable(route = NoteMenuDestiny.noteMenu()) { Box {} }
            }
        }

        waitForIdle()
        return navController
    }

    private fun NavHostController.currentFolderId(): String? = currentBackStackEntry?.folderId()

    private fun androidx.navigation.NavBackStackEntry.folderId(): String? =
        savedStateHandle.get<String?>(NAVIGATION_PATH)
}
