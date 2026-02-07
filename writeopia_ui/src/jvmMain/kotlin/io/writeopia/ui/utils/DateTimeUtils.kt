package io.writeopia.ui.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

actual fun getCurrentDateFormatted(): String {
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    return now.format(formatter)
}

actual fun getCurrentDateTimeFormatted(): String {
    val now = LocalDateTime.now()
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    return "${now.format(dateFormatter)} ${now.format(timeFormatter)}"
}
