package io.writeopia.ui.utils

import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpanTest {

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
