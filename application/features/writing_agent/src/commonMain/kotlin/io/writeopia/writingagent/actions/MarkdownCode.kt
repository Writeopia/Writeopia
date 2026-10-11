package io.writeopia.writingagent.actions

/**
 * Reads the code the AI answers with. The code inside the first fenced block (```lang ... ```)
 * wins, without the fence and the language; an answer without fences is taken whole.
 *
 * A block closes only on a line that is a fence of the same character as the opener (backticks
 * or tildes), at least as long, with nothing but whitespace after it, like Markdown says. A
 * shorter fence, or backticks inside a line of code, stay part of the code.
 */
object MarkdownCode {

    private class Fence(val char: Char, val length: Int) {
        fun closes(line: String): Boolean {
            val trimmed = line.trim()
            return trimmed.length >= length && trimmed.all { it == char }
        }

        companion object {
            fun opening(line: String): Fence? {
                val trimmed = line.trimStart()
                val char = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
                val length = trimmed.takeWhile { it == char }.length
                return if (length >= 3) Fence(char, length) else null
            }
        }
    }

    fun parse(markdown: String): String {
        val lines = markdown.lines()
        val openIndex = lines.indexOfFirst { Fence.opening(it) != null }
        if (openIndex < 0) return markdown.trimEnd().trimStart('\n')

        val fence = Fence.opening(lines[openIndex])!!
        val body = lines.drop(openIndex + 1)
        val closeIndex = body.indexOfFirst(fence::closes).takeIf { it >= 0 } ?: body.size

        return body.take(closeIndex).joinToString("\n").trimEnd().trimStart('\n')
    }
}
