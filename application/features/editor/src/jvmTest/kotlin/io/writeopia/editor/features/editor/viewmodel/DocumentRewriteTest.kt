package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentRewriteTest {

    private val spreadsheet = StoryStep(type = StoryTypes.SPREADSHEET.type)
    private val image = StoryStep(type = StoryTypes.IMAGE.type, url = "pic.png")

    private val stories: Map<Long, StoryStep> = mapOf(
        0L to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
        1L to StoryStep(type = StoryTypes.TEXT.type, text = "Intro", tags = setOf(TagInfo(Tag.H2))),
        2L to StoryStep(type = StoryTypes.TEXT.type, text = "A paragraph."),
        3L to spreadsheet,
        4L to StoryStep(type = StoryTypes.CHECK_ITEM.type, text = "Done", checked = true),
        5L to StoryStep(type = StoryTypes.UNORDERED_LIST_ITEM.type, text = "Item"),
        6L to StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "fun a() {}\nfun b() {}"),
        7L to image,
        8L to StoryStep(type = StoryTypes.DIVIDER.type),
        9L to StoryStep(type = StoryTypes.LOADING.type, ephemeral = true),
        10L to StoryStep(type = StoryTypes.TEXT.type, text = "Last line."),
    )

    private fun longDocument(): Map<Long, StoryStep> = buildMap {
        put(0L, StoryStep(type = StoryTypes.TITLE.type, text = "Title"))
        repeat(100) { index ->
            put((index + 1).toLong(), StoryStep(type = StoryTypes.TEXT.type, text = "p${index + 1}"))
        }
    }

    @Test
    fun `the document goes as Markdown with placeholders for the blocks the AI cannot write`() {
        val rewrite = assertNotNull(DocumentRewrite.build(stories, cursor = 2L))

        assertEquals(1L, rewrite.from)
        assertNull(rewrite.toExclusive)
        assertEquals(
            """
            ## Intro
            A paragraph.
            [[writeopia-block-1]]
            - [x] Done
            - Item
            ```
            fun a() {}
            fun b() {}
            ```
            [[writeopia-block-2]]
            ---
            Last line.
            """.trimIndent(),
            rewrite.markdown
        )
        assertEquals(listOf(spreadsheet, image), rewrite.keptBlocksInOrder)
        assertEquals(9, rewrite.originalStories.size)
        assertEquals("Intro\nA paragraph.\nDone\nItem\nfun a() {}\nfun b() {}\nLast line.", rewrite.originalText)
    }

    @Test
    fun `the answer comes back as stories with the blocks in their place`() {
        val rewrite = assertNotNull(DocumentRewrite.build(stories, cursor = null))

        val parsed = rewrite.parse(
            """
            ## Intro
            A **bold** paragraph.
            [[writeopia-block-1]]
            - [ ] Not done
            - Item
            ```
            fun c() {}
            ```
            [[writeopia-block-2]]
            ---
            The last line, edited.

            """.trimIndent()
        )

        val steps = parsed.stories
        assertEquals(9, steps.size)
        assertEquals(setOf(TagInfo(Tag.H2)), steps[0].tags)
        assertEquals("Intro", steps[0].text)
        assertEquals("A bold paragraph.", steps[1].text)
        assertTrue(steps[1].spans.any { it.span == Span.BOLD })
        assertEquals(spreadsheet.id, steps[2].id)
        assertEquals(StoryTypes.CHECK_ITEM.type, steps[3].type)
        assertEquals(false, steps[3].checked)
        assertEquals("Not done", steps[3].text)
        assertEquals(StoryTypes.UNORDERED_LIST_ITEM.type, steps[4].type)
        assertEquals(StoryTypes.CODE_BLOCK.type, steps[5].type)
        assertEquals("fun c() {}", steps[5].text)
        assertEquals(image.id, steps[6].id)
        assertEquals(StoryTypes.DIVIDER.type, steps[7].type)
        assertEquals("The last line, edited.", steps[8].text)
        assertEquals(emptyList(), parsed.pendingBlocks)
    }

    @Test
    fun `the blocks the answer leaves out are pending`() {
        val rewrite = assertNotNull(DocumentRewrite.build(stories, cursor = null))

        val parsed = rewrite.parse("Only text\n[[writeopia-block-2]]")

        assertEquals(listOf("Only text", null), parsed.stories.map { it.text })
        assertEquals(image.id, parsed.stories[1].id)
        assertEquals(listOf(spreadsheet), parsed.pendingBlocks)
    }

    @Test
    fun `an open code fence shows its code while the answer streams`() {
        val rewrite = assertNotNull(DocumentRewrite.build(stories, cursor = null))

        val parsed = rewrite.parse("Text\n```\nfun a()")

        assertEquals(StoryTypes.CODE_BLOCK.type, parsed.stories[1].type)
        assertEquals("fun a()", parsed.stories[1].text)
    }

    @Test
    fun `a long document is cut to the paragraphs around the cursor`() {
        val rewrite = assertNotNull(DocumentRewrite.build(longDocument(), cursor = 50L, limit = 40))

        // Each line costs 4 characters, so 10 of them fit: the cursor's, then one on each side.
        assertEquals(45L, rewrite.from)
        assertEquals(55L, rewrite.toExclusive)
        assertEquals((45..54).joinToString("\n") { "p$it" }, rewrite.markdown)
        assertEquals(10, rewrite.originalStories.size)
    }

    @Test
    fun `a cut at the end of the document reaches the end`() {
        val rewrite = assertNotNull(DocumentRewrite.build(longDocument(), cursor = 100L, limit = 40))

        assertNull(rewrite.toExclusive)
        assertTrue(rewrite.markdown.endsWith("p100"))
    }

    @Test
    fun `the restored region keeps the blocks and renews the text ids`() {
        val rewrite = assertNotNull(DocumentRewrite.build(stories, cursor = null))

        val restored = rewrite.restoredStories()

        assertEquals(rewrite.originalStories.map { it.text }, restored.map { it.text })
        assertEquals(spreadsheet.id, restored[2].id)
        assertNotEquals(rewrite.originalStories[1].id, restored[1].id)
    }

    @Test
    fun `a block the answer repeats stands once`() {
        val rewrite = assertNotNull(DocumentRewrite.build(stories, cursor = null))

        val parsed = rewrite.parse("Text\n[[writeopia-block-1]]\n[[writeopia-block-1]]\nMore")

        assertEquals(listOf("Text", null, "More"), parsed.stories.map { story -> story.text })
        assertEquals(1, parsed.stories.count { story -> story.id == spreadsheet.id })
        assertEquals(listOf(image), parsed.pendingBlocks)
    }

    @Test
    fun `a paragraph alone over the limit cannot be rewritten`() {
        val huge = mapOf(
            0L to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            1L to StoryStep(type = StoryTypes.TEXT.type, text = "short"),
            2L to StoryStep(type = StoryTypes.TEXT.type, text = "x".repeat(50)),
        )

        assertNull(DocumentRewrite.build(huge, cursor = 2L, limit = 40))
        assertNotNull(DocumentRewrite.build(huge, cursor = 1L, limit = 40))
    }

    @Test
    fun `an empty document has nothing to rewrite`() {
        assertNull(DocumentRewrite.build(mapOf(0L to StoryStep(type = StoryTypes.TITLE.type, text = "T")), cursor = null))
    }
}
