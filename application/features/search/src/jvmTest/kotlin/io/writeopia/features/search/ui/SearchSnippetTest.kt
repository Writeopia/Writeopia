package io.writeopia.features.search.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchSnippetTest {

    @Test
    fun `a short text is shown whole`() {
        assertEquals("buy some bananas", SearchSnippet.window("buy some\nbananas", "banana"))
    }

    @Test
    fun `a long text is cut around the first word found`() {
        val text = "a".repeat(200) + " the zebrafish samples arrived " + "b".repeat(200)

        val window = SearchSnippet.window(text, "zebra", maxLength = 60)

        assertEquals(true, window.startsWith("…") && window.endsWith("…"), window)
        assertEquals(true, window.contains("zebrafish"), window)
        assertEquals(true, window.length <= 62, window)
    }

    @Test
    fun `a long text without the word is cut from the start`() {
        val window = SearchSnippet.window("x".repeat(200), "missing", maxLength = 50)

        assertEquals("x".repeat(50) + "…", window)
    }

    @Test
    fun `the ranges of every word of the query, ignoring case, in order`() {
        assertEquals(
            listOf(0..2, 16..18, 31..33),
            SearchSnippet.ranges("The quick brown fox jumps over the lazy dog", "fox the")
        )
        assertEquals(emptyList(), SearchSnippet.ranges("some text", "!!!"))
    }
}
