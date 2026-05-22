package io.writeopia.common.utils.env

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

actual object EnvUtils {
    @OptIn(ExperimentalForeignApi::class)
    actual fun getAdminKey(): String? = getenv("WRITEOPIA_ADMIN_KEY")?.toKString()
}
