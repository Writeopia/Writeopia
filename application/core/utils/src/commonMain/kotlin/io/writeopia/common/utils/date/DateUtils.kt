package io.writeopia.common.utils.date

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val DAYS_IN_MONTHS = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
private const val DAYS_PER_YEAR = 365.25

/**
 * Formats epoch milliseconds to "Month Year" format (e.g., "September 2026").
 * Returns "Current Period" if formatting fails.
 */
fun formatMonthYear(epochMillis: Long): String {
    return try {
        val daysSinceEpoch = epochMillis / MILLIS_PER_DAY
        val year = 1970 + (daysSinceEpoch / DAYS_PER_YEAR).toInt()

        val startOfYear = ((year - 1970) * DAYS_PER_YEAR * MILLIS_PER_DAY).toLong()
        val millisInYear = epochMillis - startOfYear
        val dayOfYear = (millisInYear / MILLIS_PER_DAY).toInt().coerceIn(0, 365)

        var accumulatedDays = 0
        var month = 0
        for (i in DAYS_IN_MONTHS.indices) {
            accumulatedDays += DAYS_IN_MONTHS[i]
            if (dayOfYear < accumulatedDays) {
                month = i
                break
            }
            month = i
        }

        "${MONTH_NAMES[month]} $year"
    } catch (e: Exception) {
        "Current Period"
    }
}

/**
 * Formats a number with K/M suffixes for readability.
 * Examples: 1500 -> "1.5K", 2500000 -> "2.5M"
 */
fun formatCompactNumber(value: Long): String {
    return when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
