package io.writeopia.common.utils.env

actual object EnvUtils {
    actual fun getAdminKey(): String? = System.getenv("WRITEOPIA_ADMIN_KEY")
}
