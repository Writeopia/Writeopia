package io.writeopia.features.notifications.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.NotificationsRoute

/**
 * Navigate to the notifications screen.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToNotifications() {
    add(NotificationsRoute)
}
