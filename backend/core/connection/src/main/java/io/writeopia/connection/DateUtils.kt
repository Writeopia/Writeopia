package io.writeopia.connection

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Extension function to get the start of the month for a LocalDateTime.
 * Returns a LocalDateTime with the same year and month but day set to 1 and time set to 00:00:00.
 */
fun LocalDateTime.startOfMonth(): LocalDateTime =
    LocalDateTime(year, month, 1, 0, 0, 0, 0)

/**
 * Converts a LocalDateTime to epoch milliseconds in UTC timezone.
 */
fun LocalDateTime.toEpochMillisUtc(): Long =
    toInstant(TimeZone.UTC).toEpochMilliseconds()
