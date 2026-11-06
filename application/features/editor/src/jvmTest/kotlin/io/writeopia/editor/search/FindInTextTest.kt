package io.writeopia.editor.search

import io.writeopia.editor.features.search.FindInText
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
    fun testIgnoresCase() {
        val text = "Hello World"
        val query = "hello"
        val expected = listOf(Pair(0, 5))
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testEachWordOfTheQueryIsFoundOnItsOwn() {
        val text = "the quick brown fox jumps over the lazy dog"
        val query = "fox the"
        val expected = listOf(Pair(0, 3), Pair(16, 19), Pair(31, 34))
        val actual = FindInText.findInText(text, query)
        assertEquals(expected, actual)
    }

    @Test
    fun testWhitespaceOnlyQuery() {
        assertEquals(emptyList(), FindInText.findInText("some text", "   "))
    }

    @Test
    fun testEveryWordIsNeeded() {
        assertEquals(emptyList(), FindInText.findInText("the quick brown fox", "fox missing"))
    }

    @Test
    fun testPunctuationSeparatesTheWords() {
        // "hello—world" asks for both words; the dash is not part of them.
        assertEquals(listOf(Pair(0, 5), Pair(6, 11)), FindInText.findInText("hello world", "hello\u2014world"))
    }
}
