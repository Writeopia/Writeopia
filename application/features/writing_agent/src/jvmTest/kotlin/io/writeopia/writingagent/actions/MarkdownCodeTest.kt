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
    fun `it takes an unfenced answer whole`() {
        assertEquals("SELECT 1;", MarkdownCode.parse("SELECT 1;\n"))
    }
}
