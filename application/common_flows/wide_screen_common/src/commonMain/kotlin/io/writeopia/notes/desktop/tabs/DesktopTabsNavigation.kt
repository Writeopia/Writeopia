package io.writeopia.notes.desktop.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.NotesNavigationType
import io.writeopia.common.utils.encoding.decodeFromNavigation
import io.writeopia.navigation.notes.navigateToNoteMobile
import io.writeopia.notemenu.navigation.NAVIGATION_PATH
import io.writeopia.notemenu.navigation.NAVIGATION_TYPE
import io.writeopia.notemenu.navigation.navigateToNotes

/**
 * Lets the window's key handler reach the tabs of the app on screen. The tabs live with the
 * signed-in workspace (DesktopApp), the key handler lives with the window, so it looks here.
 */
class DesktopTabsHost {
    var state: DesktopTabsState? by mutableStateOf(null)
}

/** Where a back stack entry is, for the tab's title and its save. */
fun NavBackStackEntry.tabPlace(): TabPlace {
    val route = destination.route ?: return TabPlace.Other
    val handle = savedStateHandle

    return when {
        route.startsWith("${Destinations.EDITOR.id}/{noteId}") -> {
            val id = handle.get<String?>("noteId") ?: return TabPlace.Other
            val title = handle.get<String?>("noteTitle")?.decodeFromNavigation().orEmpty()
            TabPlace.Document(id, title)
        }

        route.startsWith(Destinations.CHOOSE_NOTE.id) -> {
            val type = handle.get<String?>(NAVIGATION_TYPE)
                ?.let { runCatching { NotesNavigationType.fromType(it) }.getOrNull() }
            when (type) {
                NotesNavigationType.ROOT, null -> TabPlace.Root
                NotesNavigationType.FOLDER ->
                    handle.get<String?>(NAVIGATION_PATH)?.let(TabPlace::Folder) ?: TabPlace.Root

                // Favorites and tags are not restored: the tab opens at Root next launch.
                NotesNavigationType.FAVORITES, NotesNavigationType.TAG -> TabPlace.Other
            }
        }

        else -> TabPlace.Other
    }
}

/** Takes a new tab where it was saved or asked to open. */
fun NavController.navigateToPlace(place: TabPlace) {
    when (place) {
        is TabPlace.Document -> navigateToNoteMobile(place.id, place.title.ifEmpty { "null" })
        is TabPlace.Folder -> navigateToNotes(NotesNavigation.Folder(place.id))
        TabPlace.Root, TabPlace.Other -> {}
    }
}
