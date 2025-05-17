package io.writeopia.common.utils

enum class Destinations(val id: String, val root: String) {
    EDITOR("note_details", "Home"),
    PRESENTATION("presentations", "Home"),
    CHOOSE_NOTE("choose_note", "Home"),
    FORCE_GRAPH("force_graph", "Home"),
    SEARCH("search", "io.writeopia.common.utils.icons.all.getSearch"),
    NOTIFICATIONS("notifications", "Notifications"),
    EDIT_FOLDER("edit_folder", "Home"),
    ACCOUNT("account", "Home"),

    AUTH_MENU_INNER_NAVIGATION("auth_menu_inner_navigation", "Home"),
    AUTH_REGISTER("auth_register", "Home"),
    AUTH_MENU("auth_menu", "Home"),
    AUTH_LOGIN("auth_login", "Home"),

    START_APP("start_app", "Home"),
    DESKTOP_AUTH("desktop_auth", "Home")
}
