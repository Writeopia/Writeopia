package io.writeopia.notemenu.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.ChooseNoteRoute
import io.writeopia.common.utils.MainAppRoute
import io.writeopia.common.utils.NotesNavigation

/**
 * Navigate to notes screen with specific folder navigation.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToNotes(navigation: NotesNavigation) {
    when (navigation) {
        is NotesNavigation.Folder -> {
            add(ChooseNoteRoute(
                navigationType = navigation.navigationType.type,
                navigationPath = navigation.id
            ))
        }

        NotesNavigation.Favorites, NotesNavigation.Root -> {
            add(ChooseNoteRoute(
                navigationType = navigation.navigationType.type,
                navigationPath = ""
            ))
        }
    }
}

/**
 * Navigate to main app (root notes view).
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToMainApp() {
    add(MainAppRoute)
}
