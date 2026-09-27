package io.writeopia.sdk.model.span

import io.writeopia.sdk.models.span.Intersection
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpanInfoTest {

    @Test
    fun `plus should preserve span identity`() {
        val first = SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
        val second = SpanInfo.create(3, 10, Span.COMMENT, "conversation-1")

        assertEquals(
            SpanInfo.create(0, 10, Span.COMMENT, "conversation-1"),
            first + second,
        )
    }

    @Test
    fun `plus should reject spans with different types`() {
        val bold = SpanInfo.create(0, 5, Span.BOLD)
        val italic = SpanInfo.create(3, 10, Span.ITALIC)

        assertFailsWith<IllegalArgumentException> {
            bold + italic
        }
    }

    @Test
    fun `plus should reject spans with different extras`() {
        val first = SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
        val second = SpanInfo.create(3, 10, Span.COMMENT, "conversation-2")

        assertFailsWith<IllegalArgumentException> {
            first + second
        }
    }

    @Test
    fun `it should be possible to verify spans outside of each other`() {
        val span1 = SpanInfo.create(start = 0, end = 5, span = Span.BOLD)
        val span2 = SpanInfo.create(start = 7, end = 10, span = Span.BOLD)

        val interception = span1.intersection(span2)

        assertEquals(Intersection.OUTSIDE, interception)
    }

    @Test
    fun `it should be possible to verify spans with partial intersection`() {
        val span1 = SpanInfo.create(start = 0, end = 5, span = Span.BOLD)
        val span2 = SpanInfo.create(start = 3, end = 10, span = Span.BOLD)

        val interception = span1.intersection(span2)

        assertEquals(Intersection.INTERSECT, interception)
    }

    @Test
    fun `it should be possible to verify with containing intersection`() {
        val span1 = SpanInfo.create(start = 0, end = 5, span = Span.BOLD)
        val span2 = SpanInfo.create(start = 2, end = 5, span = Span.BOLD)

        val interception = span1.intersection(span2)

        assertEquals(Intersection.CONTAINING, interception)
    }

    @Test
    fun `it should be possible to verify with containing intersection with inverted selection`() {
        val span1 = SpanInfo.create(start = 5, end = 0, span = Span.BOLD)
        val span2 = SpanInfo.create(start = 2, end = 5, span = Span.BOLD)

        val interception = span1.intersection(span2)

        assertEquals(Intersection.CONTAINING, interception)
    }

    @Test
    fun `it should be possible to verify with inside intersection`() {
        val span1 = SpanInfo.create(start = 3, end = 4, span = Span.BOLD)
        val span2 = SpanInfo.create(start = 2, end = 5, span = Span.BOLD)

        val interception = span1.intersection(span2)

        assertEquals(Intersection.INSIDE, interception)
    }

    @Test
    fun `it should be possible to verify with inside intersectio when spans are the same`() {
        val span1 = SpanInfo.create(start = 2, end = 5, span = Span.BOLD)
        val span2 = SpanInfo.create(start = 2, end = 5, span = Span.BOLD)

        val interception = span1.intersection(span2)

        assertEquals(Intersection.MATCH, interception)
    }

    @Test
    fun `it should be possible to verify with inside intersection with inverted selection`() {
        val span1 = SpanInfo.create(start = 4, end = 3, span = Span.BOLD)
        val span2 = SpanInfo.create(start = 2, end = 5, span = Span.BOLD)

        val interception = span1.intersection(span2)

        assertEquals(Intersection.INSIDE, interception)
    }
}
