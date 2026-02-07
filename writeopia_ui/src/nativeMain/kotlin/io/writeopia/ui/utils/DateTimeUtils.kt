package io.writeopia.ui.utils

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun getCurrentDateFormatted(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "dd/MM/yyyy"
    return formatter.stringFromDate(NSDate())
}

actual fun getCurrentDateTimeFormatted(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "dd/MM/yyyy HH:mm"
    return formatter.stringFromDate(NSDate())
}
