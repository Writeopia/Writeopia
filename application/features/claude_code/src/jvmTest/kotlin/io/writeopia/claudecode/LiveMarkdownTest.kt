package io.writeopia.claudecode

import io.writeopia.claudecode.markdown.LiveMarkdown
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** The same cases as `LiveMarkdownTests.swift`: both apps must read Markdown the same way. */
class LiveMarkdownTest {

    @Test
    fun `blocks of each kind`() {
        val blocks = LiveMarkdown.blocks(
            """
            # Big
            ### Small
            ###### Smallest

            Some text
            - a bullet
            * another
            - [ ] to do
            - [x] done
            > quoted
            ---
            ![a cat](https://example.com/cat.png)
            1. numbered
            """.trimIndent()
        )

        assertEquals(
            listOf(
                StoryTypes.TEXT,
                StoryTypes.TEXT,
                StoryTypes.TEXT,
                StoryTypes.TEXT,
                StoryTypes.UNORDERED_LIST_ITEM,
                StoryTypes.UNORDERED_LIST_ITEM,
                StoryTypes.CHECK_ITEM,
                StoryTypes.CHECK_ITEM,
                StoryTypes.TEXT,
                StoryTypes.DIVIDER,
                StoryTypes.IMAGE,
                StoryTypes.TEXT,
            ).map { it.type },
            blocks.map { it.type }
        )
        assertEquals(setOf(TagInfo(Tag.H1)), blocks[0].tags)
        assertEquals(setOf(TagInfo(Tag.H3)), blocks[1].tags)
        assertEquals(setOf(TagInfo(Tag.H4)), blocks[2].tags)
        assertEquals("Smallest", blocks[2].text)
        assertEquals("a bullet", blocks[4].text)
        assertEquals(false, blocks[6].checked)
        assertEquals("to do", blocks[6].text)
        assertEquals(true, blocks[7].checked)
        assertEquals(setOf(TagInfo(Tag.HIGH_LIGHT_BLOCK)), blocks[8].tags)
        assertEquals("quoted", blocks[8].text)
        assertEquals("https://example.com/cat.png", blocks[10].url)
        assertEquals("1. numbered", blocks[11].text)
    }

    @Test
    fun `fenced code is one block per line, kept as written`() {
        val blocks = LiveMarkdown.blocks("```kotlin\nval a = **1**\n\n  b()\n```\nafter")

        assertEquals(
            listOf(StoryTypes.CODE_BLOCK, StoryTypes.CODE_BLOCK, StoryTypes.CODE_BLOCK, StoryTypes.TEXT).map { it.type },
            blocks.map { it.type }
        )
        assertEquals(listOf("val a = **1**", "", "  b()", "after"), blocks.map { it.text })
    }

    @Test
    fun `equations on one line and over several`() {
        val blocks = LiveMarkdown.blocks("\$\$ e = mc^2 \$\$\n\$\$\n\\frac{a}{b}\n\$\$")

        assertEquals(listOf("e = mc^2", "\\frac{a}{b}"), blocks.map { it.text })
        assertEquals(StoryTypes.EQUATION.type, blocks[1].type)
    }

    @Test
    fun `inline markers become spans`() {
        val (text, spans) = LiveMarkdown.inline("A **bold** and *italic* and _this_ and ==marked== word")

        assertEquals("A bold and italic and this and marked word", text)
        assertEquals(
            setOf(
                SpanInfo.create(2, 6, Span.BOLD),
                SpanInfo.create(11, 17, Span.ITALIC),
                SpanInfo.create(22, 26, Span.ITALIC),
                SpanInfo.create(31, 37, Span.HIGHLIGHT_YELLOW),
            ),
            spans.toSet()
        )
    }

    @Test
    fun `links with a label and bare addresses`() {
        val (text, spans) = LiveMarkdown.inline("See [the docs](https://a.io/x) or https://b.io.")

        assertEquals("See the docs or https://b.io.", text)
        assertEquals(
            setOf(
                SpanInfo.create(4, 12, Span.LINK, "https://a.io/x"),
                SpanInfo.create(16, 28, Span.LINK, "https://b.io"),
            ),
            spans.toSet()
        )
    }

    @Test
    fun `markers that never close stay as text`() {
        assertEquals("2 * 3 = 6 and snake_case_name" to emptyList(), LiveMarkdown.inline("2 * 3 = 6 and snake_case_name"))
        assertEquals("**not bold" to emptyList(), LiveMarkdown.inline("**not bold"))
    }

    @Test
    fun `code and escapes are kept as text`() {
        assertEquals("call f(**x**) and *star*" to emptyList(), LiveMarkdown.inline("call `f(**x**)` and \\*star\\*"))
    }

    @Test
    fun `nested bold and italic`() {
        val (text, spans) = LiveMarkdown.inline("**bold *both***")

        assertEquals("bold both", text)
        assertEquals(setOf(SpanInfo.create(5, 9, Span.ITALIC), SpanInfo.create(0, 9, Span.BOLD)), spans.toSet())
    }

    @Test
    fun `offsets are UTF-16 like the editor`() {
        val (text, spans) = LiveMarkdown.inline("\uD83D\uDE00 **ok**")

        assertEquals("\uD83D\uDE00 ok", text)
        assertEquals(listOf(SpanInfo.create(3, 5, Span.BOLD)), spans)
    }
}
