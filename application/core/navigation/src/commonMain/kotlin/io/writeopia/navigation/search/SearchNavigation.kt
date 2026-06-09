package io.writeopia.navigation.search

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.SearchRoute

/**
 * Navigate to search screen.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToSearch() {
    add(SearchRoute)
}
