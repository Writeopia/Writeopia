package io.writeopia.sdk.model.markdown

import io.writeopia.sdk.models.markdown.InlineMarkdownParser
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineMarkdownParserTest {

    @Test
    fun `bold text should be converted to BOLD span`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "This is **bold** text"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals("This is bold text", result.text)
        assertEquals(1, result.spans.size)

        val boldSpan = result.spans.first()
        assertEquals(Span.BOLD, boldSpan.span)
        assertEquals(8, boldSpan.start)
        assertEquals(12, boldSpan.end)
    }

    @Test
    fun `italic text should be converted to ITALIC span`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "This is *italic* text"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals("This is italic text", result.text)
        assertEquals(1, result.spans.size)

        val italicSpan = result.spans.first()
        assertEquals(Span.ITALIC, italicSpan.span)
        assertEquals(8, italicSpan.start)
        assertEquals(14, italicSpan.end)
    }

    @Test
    fun `URL should be detected as LINK span`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "Check out https://example.com for more info"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        // URL detection doesn't modify text
        assertEquals("Check out https://example.com for more info", result.text)
        assertEquals(1, result.spans.size)

        val linkSpan = result.spans.first()
        assertEquals(Span.LINK, linkSpan.span)
        assertEquals(10, linkSpan.start)
        assertEquals(29, linkSpan.end)
        assertEquals("https://example.com", linkSpan.extra)
    }

    @Test
    fun `http URL should also be detected as LINK span`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "Visit http://example.com today"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals(1, result.spans.size)
        val linkSpan = result.spans.first()
        assertEquals(Span.LINK, linkSpan.span)
        assertEquals("http://example.com", linkSpan.extra)
    }

    @Test
    fun `combined bold and italic markers should work correctly`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "This is **bold** and *italic* text"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals("This is bold and italic text", result.text)
        assertEquals(2, result.spans.size)

        val boldSpan = result.spans.find { it.span == Span.BOLD }
        val italicSpan = result.spans.find { it.span == Span.ITALIC }

        assertTrue(boldSpan != null)
        assertTrue(italicSpan != null)
        assertEquals(Span.BOLD, boldSpan.span)
        assertEquals(Span.ITALIC, italicSpan.span)
    }

    @Test
    fun `multiple bold markers should create multiple spans`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "**first** and **second** bold"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals("first and second bold", result.text)
        assertEquals(2, result.spans.size)

        val spans = result.spans.sortedBy { it.start }
        assertEquals(Span.BOLD, spans[0].span)
        assertEquals(0, spans[0].start)
        assertEquals(5, spans[0].end)

        assertEquals(Span.BOLD, spans[1].span)
        assertEquals(10, spans[1].start)
        assertEquals(16, spans[1].end)
    }

    @Test
    fun `text without markdown should return unchanged`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "Plain text without any markdown"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals("Plain text without any markdown", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `null text should return unchanged story step`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = null
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals(null, result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `existing spans should be preserved`() {
        val existingSpan = SpanInfo.create(start = 0, end = 4, span = Span.UNDERLINE)
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "This **bold** text",
            spans = setOf(existingSpan)
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals("This bold text", result.text)
        assertEquals(2, result.spans.size)
        assertTrue(result.spans.any { it.span == Span.UNDERLINE })
        assertTrue(result.spans.any { it.span == Span.BOLD })
    }

    @Test
    fun `empty bold markers should not create span`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "This is **** empty"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        // Empty markers should be left as is or handled gracefully
        assertTrue(result.spans.none { it.span == Span.BOLD })
    }

    @Test
    fun `URL with path should be fully captured`() {
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "Link: https://example.com/path/to/resource?query=value"
        )

        val result = InlineMarkdownParser.parseMarkdown(storyStep)

        assertEquals(1, result.spans.size)
        val linkSpan = result.spans.first()
        assertEquals("https://example.com/path/to/resource?query=value", linkSpan.extra)
    }
}
