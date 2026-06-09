package io.writeopia.navigation.notes

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.ChooseNoteRoute
import io.writeopia.common.utils.NotesNavigation

/**
 * Navigate to note menu with specific navigation.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToNoteMenu(notesNavigation: NotesNavigation) {
    add(ChooseNoteRoute(
        navigationType = notesNavigation.navigationType.type,
        navigationPath = if (notesNavigation is NotesNavigation.Folder) notesNavigation.id else ""
    ))
}
