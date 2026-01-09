package io.writeopia.features.notifications.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.features.notifications.NotificationsScreen
import io.writeopia.common.utils.Destinations

object NotificationsDestiny {
    fun notifications() = Destinations.NOTIFICATIONS.id
}

fun NavGraphBuilder.notificationsNavigation(
    navigationClick: () -> Unit,
    navigationBar: @Composable () -> Unit
) {
    composable(
        route = NotificationsDestiny.notifications(),
    ) { _ ->
        NotificationsScreen(navigationClick = navigationClick, bottomBar = navigationBar)
    }
}
