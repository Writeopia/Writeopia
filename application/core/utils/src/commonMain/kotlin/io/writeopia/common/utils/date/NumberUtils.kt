package io.writeopia.common.utils.date

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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

/**
 * Formats an epoch milliseconds timestamp as "MM/YYYY" (e.g., "01/2024").
 */
fun formatMonthYear(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val dateTime = instant.toLocalDateTime(TimeZone.UTC)
    val month = dateTime.month.ordinal + 1
    return "$month/${dateTime.year}"
}

private fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
