package io.writeopia.ui.utils

import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpanTest {

    @Test
    fun `selected deletion before a span should clip and move the surviving range`() {
        val comment = SpanInfo.create(2, 8, Span.COMMENT, "conversation-1")

        val result = Spans.recalculateSpans(
            spans = setOf(comment),
            oldText = "abcdefghij",
            newText = "efghij",
            oldSelectionStart = 0,
            oldSelectionEnd = 4,
            newSelectionStart = 0,
        )

        assertEquals(
            setOf(SpanInfo.create(0, 4, Span.COMMENT, "conversation-1")),
            result,
        )
    }

    @Test
    fun `selected deletion after a span start should only remove the overlap`() {
        val comment = SpanInfo.create(2, 8, Span.COMMENT, "conversation-1")

        val result = Spans.recalculateSpans(
            spans = setOf(comment),
            oldText = "abcdefghij",
            newText = "abcde",
            oldSelectionStart = 5,
            oldSelectionEnd = 10,
            newSelectionStart = 5,
        )

        assertEquals(
            setOf(SpanInfo.create(2, 5, Span.COMMENT, "conversation-1")),
            result,
        )
    }

    @Test
    fun `deleting the full comment range should remove the span`() {
        val comment = SpanInfo.create(2, 8, Span.COMMENT, "conversation-1")

        val result = Spans.recalculateSpans(
            spans = setOf(comment),
            oldText = "abcdefghij",
            newText = "abij",
            oldSelectionStart = 2,
            oldSelectionEnd = 8,
            newSelectionStart = 2,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `backspace before a span should move it without changing identity`() {
        val comment = SpanInfo.create(2, 8, Span.COMMENT, "conversation-1")

        val result = Spans.recalculateSpans(
            spans = setOf(comment),
            oldText = "abcdefghij",
            newText = "acdefghij",
            oldSelectionStart = 2,
            oldSelectionEnd = 2,
            newSelectionStart = 1,
        )

        assertEquals(
            setOf(SpanInfo.create(1, 7, Span.COMMENT, "conversation-1")),
            result,
        )
    }

    @Test
    fun `inserting inside a link should not link the inserted text`() {
        val link = SpanInfo.create(2, 8, Span.LINK, "https://writeopia.io")

        val result = Spans.recalculateSpans(
            spans = setOf(link),
            oldText = "abcdefghij",
            newText = "abcdXXefghij",
            oldSelectionStart = 4,
            oldSelectionEnd = 4,
            newSelectionStart = 6,
        )

        assertEquals(
            setOf(
                SpanInfo.create(2, 4, Span.LINK, "https://writeopia.io"),
                SpanInfo.create(6, 10, Span.LINK, "https://writeopia.io"),
            ),
            result,
        )
    }

    @Test
    fun `recalculate spans should preserve comment identity while resizing`() {
        val comment = SpanInfo.create(2, 8, Span.COMMENT, "conversation-1")

        val result = Spans.recalculateSpans(
            spans = setOf(comment),
            position = 4,
            change = -2,
        )

        assertEquals(
            setOf(SpanInfo.create(2, 6, Span.COMMENT, "conversation-1")),
            result,
        )
    }

    @Test
    fun `recalculate spans should preserve comment identity while moving`() {
        val comment = SpanInfo.create(4, 8, Span.COMMENT, "conversation-1")

        val result = Spans.recalculateSpans(
            spans = setOf(comment),
            position = 2,
            change = 2,
        )

        assertEquals(
            setOf(SpanInfo.create(6, 10, Span.COMMENT, "conversation-1")),
            result,
        )
    }

    @Test
    fun `recalculate spans should keep separate comment conversations independent`() {
        val first = SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
        val second = SpanInfo.create(10, 15, Span.COMMENT, "conversation-2")

        val result = Spans.recalculateSpans(
            spans = setOf(first, second),
            position = 3,
            change = -2,
        )

        assertEquals(
            setOf(
                SpanInfo.create(0, 3, Span.COMMENT, "conversation-1"),
                SpanInfo.create(8, 13, Span.COMMENT, "conversation-2"),
            ),
            result,
        )
    }

    @Test
    fun `recalculate spans should remove empty ranges`() {
        val comment = SpanInfo.create(2, 4, Span.COMMENT, "conversation-1")

        val result = Spans.recalculateSpans(
            spans = setOf(comment),
            position = 2,
            change = -2,
        )

        assertTrue(result.isEmpty())
    }
}
