package io.writeopia.writingagent.actions

/**
 * Reads the code the AI answers with. The code inside the first fenced block (```lang ... ```)
 * wins, without the fence and the language; an answer without fences is taken whole.
 */
object MarkdownCode {

    private val fence = Regex("""```[^\n]*\n([\s\S]*?)```""")

    fun parse(markdown: String): String =
        (fence.find(markdown)?.groupValues?.get(1) ?: markdown).trimEnd().trimStart('\n')
}
