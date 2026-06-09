package io.writeopia.navigation.notes

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.AccountRoute
import io.writeopia.common.utils.ChooseNoteRoute
import io.writeopia.common.utils.EditorRoute
import io.writeopia.common.utils.NotesNavigation

/**
 * Navigation helper class for Navigation 3.
 * Provides type-safe navigation operations using Route objects.
 */
class NavigationActions(private val backStack: NavBackStack<NavKey>) {

    fun navigateToNote(id: String, title: String) {
        backStack.add(EditorRoute(noteId = id, noteTitle = title))
    }

    fun navigateToNewNote(parentFolderId: String = "root") {
        backStack.add(EditorRoute(parentFolderId = parentFolderId))
    }

    fun navigateToAccount() {
        backStack.add(AccountRoute)
    }

    fun navigateToFolder(navigation: NotesNavigation) {
        backStack.add(
            ChooseNoteRoute(
                navigationType = navigation.navigationType.type,
                navigationPath = if (navigation is NotesNavigation.Folder) navigation.id else ""
            )
        )
    }

    fun navigateBack(): NavKey? {
        return backStack.removeLastOrNull()
    }
}

/**
 * Extension function to create NavigationActions from a backStack.
 */
fun NavBackStack<NavKey>.toNavigationActions(): NavigationActions {
    return NavigationActions(this)
}
