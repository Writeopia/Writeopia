package io.writeopia.editor.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.EditorRoute
import io.writeopia.common.utils.PresentationRoute

/**
 * Navigate to editor with an existing note.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToEditor(noteId: String, noteTitle: String? = null) {
    add(EditorRoute(noteId = noteId, noteTitle = noteTitle))
}

/**
 * Navigate to editor to create a new note.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToNewNote(parentFolderId: String = "root") {
    add(EditorRoute(parentFolderId = parentFolderId))
}

/**
 * Navigate to presentation mode.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToPresentation(documentId: String) {
    add(PresentationRoute(documentId = documentId))
}
