package io.writeopia.common.utils.env

expect object EnvUtils {
    fun getAdminKey(): String?

    fun getLocalAiUrl(): String?

    /** Whether the app runs on a macOS desktop, where the window buttons sit in the top left. */
    fun isMacOs(): Boolean
}
