package io.writeopia.ui.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual fun getCurrentDateFormatted(): String {
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return now.format(formatter)
}

actual fun getCurrentDateTimeFormatted(): String {
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    return now.format(formatter)
}
