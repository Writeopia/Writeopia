package io.writeopia.navigation.presentation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.PresentationRoute

/**
 * Navigate to presentation mode for a document.
 * Updated for Navigation 3 using type-safe Route objects.
 */
fun NavBackStack<NavKey>.navigateToPresentation(noteId: String) {
    add(PresentationRoute(noteId))
}
