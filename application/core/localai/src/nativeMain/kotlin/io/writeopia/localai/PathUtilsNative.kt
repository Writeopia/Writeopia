package io.writeopia.localai

import platform.posix.getenv
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
actual fun expandPath(path: String): String {
    return if (path.startsWith("~")) {
        val home = getenv("HOME")?.toKString() ?: ""
        path.replaceFirst("~", home)
    } else {
        path
    }
}
