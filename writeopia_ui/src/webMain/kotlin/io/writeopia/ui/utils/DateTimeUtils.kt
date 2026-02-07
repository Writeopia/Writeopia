package io.writeopia.ui.utils

import kotlin.js.Date

actual fun getCurrentDateFormatted(): String {
    val now = Date()
    return now.toLocaleDateString()
}

actual fun getCurrentDateTimeFormatted(): String {
    val now = Date()
    return "${now.toLocaleDateString()} ${now.toLocaleTimeString()}"
}
