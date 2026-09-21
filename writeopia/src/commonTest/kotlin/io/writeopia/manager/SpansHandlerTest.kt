package io.writeopia.manager

import io.writeopia.sdk.manager.SpansHandler
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpansHandlerTest {

    @Test
    fun `it should be possible to add a span`() {
        val boldSpan = SpanInfo.create(start = 0, end = 5, Span.BOLD)
        val otherBoldSpan = SpanInfo.create(start = 7, end = 10, Span.BOLD)
        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), otherBoldSpan)

        assertTrue { newSpans.contains(otherBoldSpan) }
    }

    @Test
    fun `spans should be ordered before insertion`() {
        val boldSpan = SpanInfo.create(start = 0, end = 5, Span.BOLD)
        val otherBoldSpan = SpanInfo.create(start = 10, end = 7, Span.BOLD)
        val expected = SpanInfo.create(start = 7, end = 10, Span.BOLD)

        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), otherBoldSpan)

        assertTrue { newSpans.contains(expected) }
    }

    @Test
    fun `it should be possible to remove a span`() {
        val boldSpan = SpanInfo.create(start = 0, end = 5, Span.BOLD)
        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), boldSpan)

        assertFalse { newSpans.contains(boldSpan) }
    }

    @Test
    fun `it should be possible to expand a span`() {
        val boldSpan = SpanInfo.create(start = 0, end = 5, Span.BOLD)
        val boldSpan1 = SpanInfo.create(start = 3, end = 10, Span.BOLD)

        val expected = SpanInfo.create(start = 0, end = 10, Span.BOLD)
        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), boldSpan1)

        assertEquals(setOf(expected), newSpans)
    }

    @Test
    fun `it should be possible to expand a span with inverted selection`() {
        val boldSpan = SpanInfo.create(start = 5, end = 0, Span.BOLD)
        val boldSpan1 = SpanInfo.create(start = 10, end = 3, Span.BOLD)

        val expected = SpanInfo.create(start = 0, end = 10, Span.BOLD)
        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), boldSpan1)

        assertEquals(setOf(expected), newSpans)
    }

    @Test
    fun `adding a span inside another span should split it`() {
        val boldSpan = SpanInfo.create(start = 0, end = 10, Span.BOLD)
        val boldSpan1 = SpanInfo.create(start = 2, end = 8, Span.BOLD)

        val expected = setOf(
            SpanInfo.create(start = 0, end = 2, Span.BOLD),
            SpanInfo.create(start = 8, end = 10, Span.BOLD)
        )

        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), boldSpan1)

        assertEquals(expected, newSpans)
    }

    @Test
    fun `when adding different spans they should live together`() {
        val boldSpan = SpanInfo.create(start = 0, end = 5, Span.BOLD)
        val italicSpan = SpanInfo.create(start = 0, end = 5, Span.ITALIC)

        val expected = setOf(boldSpan, italicSpan)
        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), italicSpan)

        assertEquals(expected, newSpans)
    }

    @Test
    fun `comment spans from different conversations should live together`() {
        val first = SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
        val second = SpanInfo.create(2, 7, Span.COMMENT, "conversation-2")

        val result = SpansHandler.toggleSpans(setOf(first), second)

        assertEquals(setOf(first, second), result)
    }

    @Test
    fun `removing one comment should not remove another conversation`() {
        val first = SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
        val second = SpanInfo.create(0, 5, Span.COMMENT, "conversation-2")

        val result = SpansHandler.toggleSpans(setOf(first, second), first)

        assertEquals(setOf(second), result)
    }

    @Test
    fun `splitting a comment span should preserve the conversation id`() {
        val comment = SpanInfo.create(0, 10, Span.COMMENT, "conversation-1")
        val selection = SpanInfo.create(2, 8, Span.COMMENT, "conversation-1")

        val result = SpansHandler.toggleSpans(setOf(comment), selection)

        assertEquals(
            setOf(
                SpanInfo.create(0, 2, Span.COMMENT, "conversation-1"),
                SpanInfo.create(8, 10, Span.COMMENT, "conversation-1"),
            ),
            result,
        )
    }

    @Test
    fun `intersecting comment spans with the same conversation should preserve identity`() {
        val first = SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
        val second = SpanInfo.create(3, 10, Span.COMMENT, "conversation-1")

        val result = SpansHandler.toggleSpans(setOf(first), second)

        assertEquals(
            setOf(SpanInfo.create(0, 10, Span.COMMENT, "conversation-1")),
            result,
        )
    }

    @Test
    fun `intersecting fragmented spans with the same identity should normalize`() {
        val first = SpanInfo.create(0, 2, Span.COMMENT, "conversation-1")
        val second = SpanInfo.create(8, 10, Span.COMMENT, "conversation-1")
        val bridge = SpanInfo.create(1, 9, Span.COMMENT, "conversation-1")

        val result = SpansHandler.toggleSpans(setOf(first, second), bridge)

        assertEquals(
            setOf(SpanInfo.create(0, 10, Span.COMMENT, "conversation-1")),
            result,
        )
    }

    @Test
    fun `bulk comment spans should preserve conversation identity`() {
        val existing = SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
        val stories = mapOf(
            0.0 to StoryStep(
                type = io.writeopia.sdk.models.story.StoryTypes.TEXT.type,
                text = "first",
                spans = setOf(existing),
            ),
            1.0 to StoryStep(
                type = io.writeopia.sdk.models.story.StoryTypes.TEXT.type,
                text = "second",
            ),
        )

        val result = SpansHandler.toggleSpansForManyStories(
            stories,
            Span.COMMENT,
            "conversation-2",
        )

        assertEquals(
            setOf(
                existing,
                SpanInfo.create(0, 5, Span.COMMENT, "conversation-2"),
            ),
            result.getValue(0.0).spans,
        )
        assertEquals(
            setOf(SpanInfo.create(0, 6, Span.COMMENT, "conversation-2")),
            result.getValue(1.0).spans,
        )
    }

    @Test
    fun `bulk adding an existing comment identity should normalize the range`() {
        val partial = SpanInfo.create(1, 3, Span.COMMENT, "conversation-1")
        val stories = mapOf(
            0.0 to StoryStep(
                type = io.writeopia.sdk.models.story.StoryTypes.TEXT.type,
                text = "first",
                spans = setOf(partial),
            ),
            1.0 to StoryStep(
                type = io.writeopia.sdk.models.story.StoryTypes.TEXT.type,
                text = "second",
            ),
        )

        val result = SpansHandler.toggleSpansForManyStories(
            stories,
            Span.COMMENT,
            "conversation-1",
        )

        assertEquals(
            setOf(SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")),
            result.getValue(0.0).spans,
        )
        assertEquals(
            setOf(SpanInfo.create(0, 6, Span.COMMENT, "conversation-1")),
            result.getValue(1.0).spans,
        )
    }

    @Test
    fun `bulk removing one comment identity should preserve another`() {
        val first = SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
        val second = SpanInfo.create(0, 5, Span.COMMENT, "conversation-2")
        val stories = mapOf(
            0.0 to StoryStep(
                type = io.writeopia.sdk.models.story.StoryTypes.TEXT.type,
                text = "first",
                spans = setOf(first, second),
            ),
            1.0 to StoryStep(
                type = io.writeopia.sdk.models.story.StoryTypes.TEXT.type,
                text = "second",
                spans = setOf(
                    SpanInfo.create(0, 6, Span.COMMENT, "conversation-2")
                ),
            ),
        )

        val result = SpansHandler.toggleSpansForManyStories(
            stories,
            Span.COMMENT,
            "conversation-2",
        )

        assertEquals(setOf(first), result.getValue(0.0).spans)
        assertTrue(result.getValue(1.0).spans.isEmpty())
    }

    @Test
    fun `when adding a containing span only the new one should live`() {
        val boldSpan = SpanInfo.create(start = 2, end = 10, Span.BOLD)
        val boldSpan1 = SpanInfo.create(start = 0, end = 15, Span.BOLD)

        val expected = setOf(boldSpan1)

        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), boldSpan1)

        assertEquals(expected, newSpans)
    }

    @Test
    fun `it should be possible to crop a span from end`() {
        val boldSpan = SpanInfo.create(start = 0, end = 10, Span.BOLD)
        val boldSpan1 = SpanInfo.create(start = 5, end = 10, Span.BOLD)

        val expected = setOf(boldSpan.copy(end = 5))
        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), boldSpan1)

        assertEquals(expected, newSpans)
    }

    @Test
    fun `it should be possible to crop a span from start`() {
        val boldSpan = SpanInfo.create(start = 0, end = 10, Span.BOLD)
        val boldSpan1 = SpanInfo.create(start = 0, end = 5, Span.BOLD)

        val expected = setOf(boldSpan.copy(start = 5))
        val newSpans = SpansHandler.toggleSpans(setOf(boldSpan), boldSpan1)

        assertEquals(expected, newSpans)
    }
}
