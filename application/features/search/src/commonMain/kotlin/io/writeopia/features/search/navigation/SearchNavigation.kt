package io.writeopia.features.search.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.SearchRoute

/**
 * Navigate to the search screen.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToSearch() {
    add(SearchRoute)
}
