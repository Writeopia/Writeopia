package io.writeopia.account.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.AccountRoute

/**
 * Navigate to account/settings screen.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToAccount() {
    add(AccountRoute)
}
