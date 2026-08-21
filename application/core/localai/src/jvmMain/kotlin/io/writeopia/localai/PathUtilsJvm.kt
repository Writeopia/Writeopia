package io.writeopia.localai

import java.io.File

actual fun expandPath(path: String): String {
    val expanded = if (path.startsWith("~")) {
        val home = System.getProperty("user.home") ?: ""
        path.replaceFirst("~", home)
    } else {
        path
    }

    // Check if file exists and log
    val file = File(expanded)
    println("[PathUtils] Path: $path -> $expanded (exists: ${file.exists()}, isFile: ${file.isFile})")

    return expanded
}
