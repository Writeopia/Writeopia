package io.writeopia.sdk.export

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentToMarkdownTest {

    @Test
    fun testMarkDown() {
        val content = mapOf(
            0L to StoryStep(
                type = StoryTypes.TEXT.type,
                text = "Title!!",
                tags = setOf(TagInfo(Tag.H1))
            ),
            1L to StoryStep(type = StoryTypes.TEXT.type, text = "some text"),
            2L to StoryStep(
                type = StoryTypes.TEXT.type,
                text = "Subtitle!!",
                tags = setOf(TagInfo(Tag.H2))
            )
        )

        val parsedContent = DocumentToMarkdown.parse(content, prettyPrint = true)
        val expected = buildString {
            appendLine()
            appendLine("# Title!!")
            appendLine()
            appendLine("some text")
            appendLine()
            appendLine("## Subtitle!!")
            appendLine()
        }

        assertEquals(expected, parsedContent)
    }

    @Test
    fun testMarkdownPrettyPrintFalse() {
        val content = mapOf(
            0L to StoryStep(
                type = StoryTypes.TEXT.type,
                text = "Title!!",
                tags = setOf(TagInfo(Tag.H1))
            ),
            1L to StoryStep(type = StoryTypes.TEXT.type, text = "some text"),
            2L to StoryStep(
                type = StoryTypes.TEXT.type,
                text = "Subtitle!!",
                tags = setOf(TagInfo(Tag.H2))
            ),
            3L to StoryStep(
                type = StoryTypes.CHECK_ITEM.type,
                text = "Check this"
            ),
            4L to StoryStep(
                type = StoryTypes.UNORDERED_LIST_ITEM.type,
                text = "This is a list item"
            )
        )

        val parsedContent = DocumentToMarkdown.parse(content, prettyPrint = false)
        val expected = buildString {
            appendLine("# Title!!")
            appendLine("some text")
            appendLine("## Subtitle!!")
            appendLine("[] Check this")
            appendLine("- This is a list item")
        }

        assertEquals(expected, parsedContent)
    }

    @Test
    fun `link cards become links, with labels and addresses that can not break them`() {
        val content = mapOf(
            0L to StoryStep(type = StoryTypes.LINK_CARD.type, url = "https://writeopia.io", text = "A text editor."),
            1L to StoryStep(type = StoryTypes.LINK_CARD.type, url = "https://writeopia.io"),
            2L to StoryStep(type = StoryTypes.LINK_CARD.type, url = "https://writeopia.io", text = "Editor [beta] done"),
            3L to StoryStep(type = StoryTypes.LINK_CARD.type, url = "https://w.io/a b(c)", text = "Odd address")
        )

        val expected = buildString {
            appendLine("[A text editor.](https://writeopia.io)")
            appendLine("[https://writeopia.io](https://writeopia.io)")
            appendLine("[Editor \\[beta\\] done](https://writeopia.io)")
            appendLine("[Odd address](<https://w.io/a b(c)>)")
        }

        assertEquals(expected, DocumentToMarkdown.parse(content))
    }
}
