package io.writeopia.sqldelight.fts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtsQueryTest {

    @Test
    fun `every word is lowercased and matched by prefix`() {
        assertEquals("hello* world*", FtsQuery.fts4Match("Hello WORLD"))
    }

    @Test
    fun `operators and quotes are dropped, so the expression can't break`() {
        assertEquals("foo* bar*", FtsQuery.fts4Match("\"foo\" -bar*:(!)"))
        // "OR" in capitals would be an operator; lowercased it is one more word.
        assertEquals("foo* or* bar*", FtsQuery.fts4Match("foo OR bar"))
    }

    @Test
    fun `nothing to search when there is no letter or digit`() {
        assertNull(FtsQuery.fts4Match(""))
        assertNull(FtsQuery.fts4Match("  !!! "))
    }

    @Test
    fun `a text matches when it holds every word, by prefix and ignoring case`() {
        assertTrue(FtsQuery.matches("The Quarterly report was approved", "quart REPORT"))
        assertFalse(FtsQuery.matches("The Quarterly report was approved", "quarterly missing"))
        assertFalse(FtsQuery.matches("anything", "!!!"))
    }
}
