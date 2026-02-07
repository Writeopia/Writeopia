package io.writeopia.ui.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DateTimeUtilsTest {

    @Test
    fun `getCurrentDateFormatted returns non-empty string`() {
        val result = getCurrentDateFormatted()
        assertTrue(result.isNotEmpty(), "Date formatted string should not be empty")
    }

    @Test
    fun `getCurrentDateTimeFormatted returns non-empty string`() {
        val result = getCurrentDateTimeFormatted()
        assertTrue(result.isNotEmpty(), "DateTime formatted string should not be empty")
    }

    @Test
    fun `getCurrentDateFormatted contains current year`() {
        val currentYear = LocalDate.now().year.toString()
        val result = getCurrentDateFormatted()
        assertTrue(
            result.contains(currentYear) || result.contains(currentYear.takeLast(2)),
            "Date should contain current year ($currentYear), got: $result"
        )
    }

    @Test
    fun `getCurrentDateTimeFormatted contains time separator`() {
        val result = getCurrentDateTimeFormatted()
        assertTrue(
            result.contains(":"),
            "DateTime should contain time with colon separator, got: $result"
        )
    }

    @Test
    fun `getCurrentDateFormatted uses US locale format when locale is US`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val result = getCurrentDateFormatted()
            // US format is M/d/yy or similar (month first)
            // The result should have the month as the first number
            val today = LocalDate.now()
            val expectedMonthFirst = "${today.monthValue}/"
            assertTrue(
                result.startsWith(expectedMonthFirst),
                "US locale should have month first. Expected to start with $expectedMonthFirst, got: $result"
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `getCurrentDateFormatted uses European locale format when locale is Germany`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val result = getCurrentDateFormatted()
            // German format is dd.MM.yy (day first with dots)
            val today = LocalDate.now()
            val dayString = today.dayOfMonth.toString().padStart(2, '0')
            assertTrue(
                result.startsWith(dayString),
                "German locale should have day first. Expected to start with $dayString, got: $result"
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `getCurrentDateFormatted uses UK locale format when locale is UK`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.UK)
            val result = getCurrentDateFormatted()
            // UK format is dd/MM/yy (day first with slashes)
            val today = LocalDate.now()
            val dayString = today.dayOfMonth.toString().padStart(2, '0')
            assertTrue(
                result.startsWith(dayString),
                "UK locale should have day first. Expected to start with $dayString, got: $result"
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `getCurrentDateTimeFormatted uses US locale format when locale is US`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val result = getCurrentDateTimeFormatted()
            val today = LocalDate.now()
            val expectedMonthFirst = "${today.monthValue}/"
            assertTrue(
                result.startsWith(expectedMonthFirst),
                "US locale datetime should have month first. Expected to start with $expectedMonthFirst, got: $result"
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `getCurrentDateTimeFormatted contains current hour`() {
        val result = getCurrentDateTimeFormatted()
        val currentHour = LocalDateTime.now().hour.toString().padStart(2, '0')
        assertTrue(
            result.contains(currentHour),
            "DateTime should contain current hour ($currentHour), got: $result"
        )
    }
}
