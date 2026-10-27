package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.utils.iterables.Rank
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

    private val stories: Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
        Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "Intro", tags = setOf(TagInfo(Tag.H2))),
        Rank.at(2) to StoryStep(type = StoryTypes.TEXT.type, text = "A paragraph."),
        Rank.at(3) to spreadsheet,
        Rank.at(4) to StoryStep(type = StoryTypes.CHECK_ITEM.type, text = "Done", checked = true),
        Rank.at(5) to StoryStep(type = StoryTypes.UNORDERED_LIST_ITEM.type, text = "Item"),
        Rank.at(6) to StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "fun a() {}\nfun b() {}"),
        Rank.at(7) to image,
        Rank.at(8) to StoryStep(type = StoryTypes.DIVIDER.type),
        Rank.at(9) to StoryStep(type = StoryTypes.LOADING.type, ephemeral = true),
        Rank.at(10) to StoryStep(type = StoryTypes.TEXT.type, text = "Last line."),
    )

    private fun longDocument(): Map<String, StoryStep> = buildMap {
        put(Rank.at(0), StoryStep(type = StoryTypes.TITLE.type, text = "Title"))
        repeat(100) { index ->
            put(Rank.at(index + 1), StoryStep(type = StoryTypes.TEXT.type, text = "p${index + 1}"))
        }
    }

    @Test
    fun `the document goes as Markdown with placeholders for the blocks the AI cannot write`() {
        val rewrite = assertNotNull(DocumentRewrite.build(stories, cursor = Rank.at(2)))

        assertEquals(Rank.at(1), rewrite.from)
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
        val rewrite = assertNotNull(DocumentRewrite.build(longDocument(), cursor = Rank.at(50), limit = 40))

        // Each line costs 4 characters, so 10 of them fit: the cursor's, then one on each side.
        assertEquals(Rank.at(45), rewrite.from)
        assertEquals(Rank.at(55), rewrite.toExclusive)
        assertEquals((45..54).joinToString("\n") { "p$it" }, rewrite.markdown)
        assertEquals(10, rewrite.originalStories.size)
    }

    @Test
    fun `a cut at the end of the document reaches the end`() {
        val rewrite = assertNotNull(DocumentRewrite.build(longDocument(), cursor = Rank.at(100), limit = 40))

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
            Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "short"),
            Rank.at(2) to StoryStep(type = StoryTypes.TEXT.type, text = "x".repeat(50)),
        )

        assertNull(DocumentRewrite.build(huge, cursor = Rank.at(2), limit = 40))
        assertNotNull(DocumentRewrite.build(huge, cursor = Rank.at(1), limit = 40))
    }

    @Test
    fun `an empty document has nothing to rewrite`() {
        assertNull(DocumentRewrite.build(mapOf(Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "T")), cursor = null))
    }
}
