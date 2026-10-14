package io.writeopia.common.utils.date

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun lessThanAMinuteIsJustNow() {
        assertEquals("Just now", formatRelativeTime(now, now))
        assertEquals("Just now", formatRelativeTime(now - 59_000, now))
    }

    @Test
    fun minutesAndHoursArePluralized() {
        assertEquals("1 minute ago", formatRelativeTime(now - minute, now))
        assertEquals("45 minutes ago", formatRelativeTime(now - 45 * minute, now))
        assertEquals("1 hour ago", formatRelativeTime(now - hour, now))
        assertEquals("23 hours ago", formatRelativeTime(now - 23 * hour, now))
    }

    @Test
    fun daysWeeksMonthsAndYears() {
        assertEquals("Yesterday", formatRelativeTime(now - day, now))
        assertEquals("Yesterday", formatRelativeTime(now - 2 * day + 1, now))
        assertEquals("2 days ago", formatRelativeTime(now - 2 * day, now))
        assertEquals("1 week ago", formatRelativeTime(now - 7 * day, now))
        assertEquals("3 weeks ago", formatRelativeTime(now - 25 * day, now))
        assertEquals("1 month ago", formatRelativeTime(now - 30 * day, now))
        assertEquals("11 months ago", formatRelativeTime(now - 364 * day, now))
        assertEquals("1 year ago", formatRelativeTime(now - 365 * day, now))
        assertEquals("2 years ago", formatRelativeTime(now - 800 * day, now))
    }
}
