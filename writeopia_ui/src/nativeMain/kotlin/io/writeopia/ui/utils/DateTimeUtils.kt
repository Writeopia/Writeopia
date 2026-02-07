package io.writeopia.ui.utils

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle

actual fun getCurrentDateFormatted(): String {
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterShortStyle
    formatter.timeStyle = platform.Foundation.NSDateFormatterNoStyle
    return formatter.stringFromDate(NSDate())
}

actual fun getCurrentDateTimeFormatted(): String {
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterShortStyle
    formatter.timeStyle = NSDateFormatterShortStyle
    return formatter.stringFromDate(NSDate())
}
