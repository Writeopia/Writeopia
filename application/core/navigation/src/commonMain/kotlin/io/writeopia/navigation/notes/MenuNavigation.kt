package io.writeopia.navigation.notes

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation

fun NavController.navigateToNoteMenu(
    notesNavigation: NotesNavigation,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(
        "${Destinations.CHOOSE_NOTE.id}/${notesNavigation.navigationType.type}/path",
        builder = builder
    )
}
