package io.writeopia.common.utils.env

actual object EnvUtils {
    actual fun getAdminKey(): String? = System.getenv("WRITEOPIA_ADMIN_KEY")

    actual fun getLocalAiUrl(): String? =
        System.getenv("LOCAL_AI_URL")?.takeIf { it.isNotBlank() }

    actual fun isMacOs(): Boolean =
        System.getProperty("os.name")?.lowercase()?.contains("mac") == true
}
