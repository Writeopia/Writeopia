package io.writeopia.ui.utils

import kotlin.js.Date

actual fun getCurrentDateFormatted(): String {
    val now = Date()
    val day = now.getDate().toString().padStart(2, '0')
    val month = (now.getMonth() + 1).toString().padStart(2, '0')
    val year = now.getFullYear()
    return "$day/$month/$year"
}

actual fun getCurrentDateTimeFormatted(): String {
    val now = Date()
    val day = now.getDate().toString().padStart(2, '0')
    val month = (now.getMonth() + 1).toString().padStart(2, '0')
    val year = now.getFullYear()
    val hours = now.getHours().toString().padStart(2, '0')
    val minutes = now.getMinutes().toString().padStart(2, '0')
    return "$day/$month/$year $hours:$minutes"
}
