package io.writeopia.editor.features.search

import kotlin.test.Test
import kotlin.test.assertEquals

class FindInTextTest {

    @Test
    fun testSingleOccurrence() {
        val text = "hello world"
        val query = "world"
        val expected = listOf(Pair(6, 11))
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testMultipleOccurrences() {
        val text = "the quick brown fox jumps over the lazy dog"
        val query = "the"
        val expected = listOf(Pair(0, 3), Pair(31, 34))
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testOverlappingOccurrences() {
        val text = "ababa"
        val query = "aba"

        val expected = listOf(Pair(0, 3), Pair(2, 5))
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testNoOccurrence() {
        val text = "this is a test"
        val query = "kotlin"
        val expected = emptyList<Pair<Int, Int>>()
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testEmptyQuery() {
        val text = "some text"
        val query = ""
        val expected = emptyList<Pair<Int, Int>>()
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testEmptyText() {
        val text = ""
        val query = "query"
        val expected = emptyList<Pair<Int, Int>>()
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testQueryAtStartAndEnd() {
        val text = "test_test"
        val query = "test"
        val expected = listOf(Pair(0, 4), Pair(5, 9))
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testCaseSensitivity() {
        val text = "Hello World"
        val query = "hello" // Case mismatch
        val expected = emptyList<Pair<Int, Int>>()
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }
}
