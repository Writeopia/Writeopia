package io.writeopia.common.utils.date

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val MONTH_NAMES = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
)

/**
 * Formats epoch milliseconds to "Month Year" format (e.g., "September 2026").
 * Uses kotlinx-datetime for proper calendar conversion that handles leap years
 * and month boundaries correctly.
 * Returns "Current Period" if formatting fails.
 */
fun formatMonthYear(epochMillis: Long): String = try {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val dateTime = instant.toLocalDateTime(TimeZone.UTC)
    val monthIndex = dateTime.monthNumber - 1 // Convert 1-based to 0-based index
    "${MONTH_NAMES[monthIndex]} ${dateTime.year}"
} catch (e: Exception) {
    "Current Period"
}

/**
 * Formats a number with K/M suffixes for readability.
 * Examples: 1500 -> "1.5K", 2500000 -> "2.5M"
 */
fun formatCompactNumber(value: Long): String = when {
    value >= 1_000_000 -> {
        val scaled = value / 1_000_000.0
        "${formatOneDecimal(scaled)}M"
    }
    value >= 1_000 -> {
        val scaled = value / 1_000.0
        "${formatOneDecimal(scaled)}K"
    }
    else -> value.toString()
}

private fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
