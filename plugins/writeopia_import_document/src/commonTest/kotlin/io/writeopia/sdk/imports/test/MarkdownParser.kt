package io.writeopia.sdk.imports.test

import io.writeopia.sdk.import.markdown.MarkdownParser
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownParserTest {

    private val markdownSample = listOf(
        "# Sample Markdown Document",
        "",
        "Welcome to this **Markdown** example! This document showcases basic formatting elements.",
        "",
        "---",
        "",
        "## Table of Contents",
        "",
        "1. [Headings](#headings)",
        "2. [Lists](#lists)",
        "3. [Code](#code)",
        "4. [Blockquote](#blockquote)",
        "5. [Links and Images](#links-and-images)",
        "",
        "---",
        "",
        "## Headings",
        "",
        "Use `#` for H1, `##` for H2, and so on:",
        "",
        "### This is a Heading 3",
        "",
        "---",
        "",
        "## Lists",
        "",
        "### Unordered List",
        "",
        "- Apples",
        "- Bananas",
        "- Cherries",
        "",
        "### Ordered List",
        "",
        "1. First item",
        "2. Second item",
        "3. Third item",
        "",
        "---",
        "",
        "## Code",
        "",
        "Inline code looks like `this`.",
        "",
        "#### Code Block",
        "",
        "```python",
        "def hello_world():",
        "    print(\"Hello, world!\")",
        "```",
        "",
        "---",
        "",
        "## Blockquote",
        "",
        "> “Markdown is not a replacement for HTML, but it's a syntax for writing for the web.”  ",
        "> — *John Gruber*",
        "",
        "---",
        "",
        "## Links and Images",
        "",
        "- [Visit OpenAI](https://www.openai.com)",
        "- ![Sample Image](https://via.placeholder.com/150)",
        "",
        "---",
        "",
        "## Task List",
        "",
        "- [x] Learn Markdown",
        "- [ ] Write documentation",
        "- [ ] Share with team",
        "",
        "---",
        "",
        "Thanks for reading!"
    )

    @Test
    fun `it should parse the document`() {
        val results = MarkdownParser.parse(markdownSample)

        assertEquals(results.first().type.number, StoryTypes.TITLE.type.number)
    }

    @Test
    fun `empty lines should not be removed`() {
        val sample = listOf(
            "# Sample Markdown Document",
            "",
            "Welcome to this **Markdown** example!",
        )

        val expected = listOf(
            StoryTypes.TITLE.type.number to "Sample Markdown Document",
            StoryTypes.TEXT.type.number to "",
            StoryTypes.TEXT.type.number to "Welcome to this **Markdown** example!",
        )

        val results = MarkdownParser.parse(sample)
        assertEquals(results.size, 3)
        val parsedResults = results.map { storyStepApi ->
            storyStepApi.type.number to storyStepApi.text
        }

        // It is necessary to compare like this because the ids won't match.
        assertEquals(expected, parsedResults)
    }

    @Test
    fun `all types should be parsed`() {
        val sample = listOf(
            "# Sample Markdown Document",
            "",
            "[] Checkitem",
            "-[] Checkitem2",
            "- Item list",
            "#",
            "---",
        )

        val expected = listOf(
            StoryTypes.TITLE.type.number to "Sample Markdown Document",
            StoryTypes.TEXT.type.number to "",
            StoryTypes.CHECK_ITEM.type.number to "Checkitem",
            StoryTypes.CHECK_ITEM.type.number to "Checkitem2",
            StoryTypes.UNORDERED_LIST_ITEM.type.number to "Item list",
            StoryTypes.TEXT.type.number to "",
            StoryTypes.DIVIDER.type.number to null,
        )

        val results = MarkdownParser.parse(sample)
        assertEquals(results.size, 7)
        val parsedResults = results.map { storyStepApi ->
            storyStepApi.type.number to storyStepApi.text
        }

        // It is necessary to compare like this because the ids won't match.
        assertEquals(expected, parsedResults)
    }
}
