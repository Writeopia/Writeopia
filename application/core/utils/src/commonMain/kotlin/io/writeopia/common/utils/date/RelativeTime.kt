@file:OptIn(ExperimentalTime::class)

package io.writeopia.common.utils.date

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS
private const val WEEK_MILLIS = 7 * DAY_MILLIS
private const val MONTH_MILLIS = 30 * DAY_MILLIS
private const val YEAR_MILLIS = 365 * DAY_MILLIS

/**
 * Formats how long ago [epochMillis] was, relative to [nowMillis], the way the native apps
 * show it: "Just now", "5 minutes ago", "Yesterday", "3 weeks ago", "2 years ago".
 */
fun formatRelativeTime(
    epochMillis: Long,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): String {
    val elapsed = nowMillis - epochMillis

    return when {
        elapsed < MINUTE_MILLIS -> "Just now"
        elapsed < HOUR_MILLIS -> plural(elapsed / MINUTE_MILLIS, "minute")
        elapsed < DAY_MILLIS -> plural(elapsed / HOUR_MILLIS, "hour")
        elapsed < 2 * DAY_MILLIS -> "Yesterday"
        elapsed < WEEK_MILLIS -> plural(elapsed / DAY_MILLIS, "day")
        elapsed < MONTH_MILLIS -> plural(elapsed / WEEK_MILLIS, "week")
        elapsed < YEAR_MILLIS -> plural(minOf(elapsed / MONTH_MILLIS, 11), "month")
        else -> plural(elapsed / YEAR_MILLIS, "year")
    }
}

private fun plural(amount: Long, unit: String): String =
    if (amount == 1L) "1 $unit ago" else "$amount ${unit}s ago"
