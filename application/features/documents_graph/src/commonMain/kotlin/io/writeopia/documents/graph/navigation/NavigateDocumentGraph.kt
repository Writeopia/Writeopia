package io.writeopia.documents.graph.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.ForceGraphRoute

/**
 * Navigate to the force graph view.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToForceGraph() {
    add(ForceGraphRoute)
}
