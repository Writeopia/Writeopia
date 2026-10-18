package io.writeopia.navigation

import io.writeopia.common.utils.Destinations

enum class NavItemName(val value: String) {
    HOME("Home"),
    FAVORITES("Favorites"),
    SEARCH("io.writeopia.common.utils.icons.all.getSearch"),
    SETTINGS("Settings");

    companion object {
        fun selectRoute(route: String): NavItemName {
            val destination = Destinations.entries.first {
                route.contains(it.id)
            }
            return entries.first { navItem ->
                navItem.value == destination.root
            }
        }
    }
}
