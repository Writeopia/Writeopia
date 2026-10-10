package io.writeopia.writingagent.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class SentenceEndsTest {

    @Test
    fun `it counts periods, exclamation and question marks`() {
        assertEquals(3, SentenceEnds.count("Hello. Is it you? Yes!"))
    }

    @Test
    fun `it ignores other punctuation`() {
        assertEquals(0, SentenceEnds.count("one, two; three: four"))
    }

    @Test
    fun `it counts nothing for no text`() {
        assertEquals(0, SentenceEnds.count(null))
        assertEquals(0, SentenceEnds.count(""))
    }
}
