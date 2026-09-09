package io.writeopia.common.utils.date

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
