package io.writeopia.sdk.imports.test

import io.writeopia.sdk.import.markdown.PresentationMarkdownParser
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationMarkdownParserTest {

    private val sample = """
        ## Slide 1

        This is a description explaining why the sky is blue! lalalala
        ---

        ### Slide 2

        Isn't it cool that I can create the second slide??

        [] This is a check box!
        - This is a list item!
        ---

        ### Thank you!!
        The presentation is over!
    """.trimIndent()

    @Test
    fun `every divider ends a slide and the heading is its title`() {
        val slides = PresentationMarkdownParser.parse(sample)

        assertEquals(listOf("Slide 1", "Slide 2", "Thank you!!"), slides.map { it.title })
        assertEquals(
            listOf("This is a description explaining why the sky is blue! lalalala"),
            slides[0].content.map { it.text }
        )
        assertEquals(
            listOf(StoryTypes.TEXT.type, StoryTypes.CHECK_ITEM.type, StoryTypes.UNORDERED_LIST_ITEM.type),
            slides[1].content.map { it.type }
        )
        assertEquals("This is a check box!", slides[1].content[1].text)
        assertEquals("This is a list item!", slides[1].content[2].text)
        assertEquals(listOf("The presentation is over!"), slides[2].content.map { it.text })
    }

    @Test
    fun `without dividers every heading starts a slide`() {
        val slides = PresentationMarkdownParser.parse(
            """
            # Intro
            Hello
            ## Part two
            - one
            - two
            """.trimIndent()
        )

        assertEquals(listOf("Intro", "Part two"), slides.map { it.title })
        assertEquals(2, slides[1].content.size)
    }

    @Test
    fun `a code fence around the whole answer is dropped`() {
        val slides = PresentationMarkdownParser.parse("```markdown\n$sample\n```\n")

        assertEquals(listOf("Slide 1", "Slide 2", "Thank you!!"), slides.map { it.title })
        assertEquals(StoryTypes.TEXT.type, slides[0].content.first().type)
    }

    @Test
    fun `empty slides are dropped and a missing heading leaves an empty title`() {
        val slides = PresentationMarkdownParser.parse(
            """
            ---

            Just a paragraph
            ---
            ---
            ## Last
            ---
            """.trimIndent()
        )

        assertEquals(2, slides.size)
        assertEquals("", slides[0].title)
        assertEquals(listOf("Just a paragraph"), slides[0].content.map { it.text })
        assertEquals("Last", slides[1].title)
        assertTrue(slides[1].content.isEmpty())
    }

    @Test
    fun `nothing gives no slides`() {
        assertTrue(PresentationMarkdownParser.parse("").isEmpty())
        assertTrue(PresentationMarkdownParser.parse("\n---\n\n").isEmpty())
    }

    @Test
    fun `inline markdown becomes spans`() {
        val slides = PresentationMarkdownParser.parse("## Title\nThis is **bold** text\n---")

        assertEquals("This is bold text", slides[0].content[0].text)
        assertTrue(slides[0].content[0].spans.isNotEmpty())
    }
}
