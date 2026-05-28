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
            0.0 to StoryStep(
                type = StoryTypes.TEXT.type,
                text = "Title!!",
                tags = setOf(TagInfo(Tag.H1))
            ),
            1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "some text"),
            2.0 to StoryStep(
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
            0.0 to StoryStep(
                type = StoryTypes.TEXT.type,
                text = "Title!!",
                tags = setOf(TagInfo(Tag.H1))
            ),
            1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "some text"),
            2.0 to StoryStep(
                type = StoryTypes.TEXT.type,
                text = "Subtitle!!",
                tags = setOf(TagInfo(Tag.H2))
            ),
            3.0 to StoryStep(
                type = StoryTypes.CHECK_ITEM.type,
                text = "Check this"
            ),
            4.0 to StoryStep(
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
}
