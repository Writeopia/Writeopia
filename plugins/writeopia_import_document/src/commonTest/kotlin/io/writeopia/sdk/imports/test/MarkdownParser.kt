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
}
