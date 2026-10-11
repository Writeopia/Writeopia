package io.writeopia.writingagent.actions

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownCodeTest {

    @Test
    fun `it takes the code of the first fenced block, without the fence and the language`() {
        val code = MarkdownCode.parse("Sure:\n```bash\nbrew install ollama\nollama run llama3\n```\nThat's it.")

        assertEquals("brew install ollama\nollama run llama3", code)
    }

    @Test
    fun `backticks inside the code do not close the block`() {
        val code = MarkdownCode.parse("```sh\necho \"```not a fence```\"\n```x\nstill code\n```\nafter")

        assertEquals("echo \"```not a fence```\"\n```x\nstill code", code)
    }

    @Test
    fun `a closing fence may be longer than the opener, and tildes work too`() {
        assertEquals("a\nb", MarkdownCode.parse("```\na\nb\n`````  \nmore"))
        assertEquals("x = 1", MarkdownCode.parse("~~~python\nx = 1\n~~~"))
    }

    @Test
    fun `a block that never closes runs to the end`() {
        assertEquals("let a = 1", MarkdownCode.parse("```swift\nlet a = 1\n"))
    }

    @Test
    fun `it takes an unfenced answer whole`() {
        assertEquals("SELECT 1;", MarkdownCode.parse("SELECT 1;\n"))
    }
}
