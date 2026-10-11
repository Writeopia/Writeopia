package io.writeopia.writingagent.actions

/**
 * Reads the list the AI answers with: one item per line, as `- item`, `* item`, `1. item` or
 * `[ ] item`. Lines that are none of those, like an introduction, are dropped.
 */
object MarkdownList {

    private val itemPrefix = Regex("""^\s*(?:[-*•]|\d+[.)])\s*(?:\[[ xX]]\s*)?(.*)$""")

    fun parse(markdown: String): List<String> =
        markdown.lines()
            .mapNotNull { line -> itemPrefix.matchEntire(line)?.groupValues?.get(1)?.trim() }
            .filter { it.isNotEmpty() }
}
