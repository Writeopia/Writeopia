package io.writeopia.navigation.notifications

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.NotificationsRoute

/**
 * Navigate to notifications screen.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToNotifications() {
    add(NotificationsRoute)
}
