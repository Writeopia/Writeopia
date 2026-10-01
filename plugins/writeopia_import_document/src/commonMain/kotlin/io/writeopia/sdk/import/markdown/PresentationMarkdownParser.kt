package io.writeopia.sdk.import.markdown

import io.writeopia.sdk.models.presentation.Slide
import io.writeopia.sdk.serialization.extensions.toModel

/**
 * Reads the Markdown the AI writes for a presentation into slides. Every line of dashes
 * (`---`) ends a slide and the first heading of a slide is its title. The lines of each slide
 * are read by [MarkdownParser], so lists, checkboxes and inline styles come out as in a document.
 *
 * Shared by the backend and the apps, so a presentation looks the same wherever it was made.
 */
object PresentationMarkdownParser {

    fun parse(markdown: String): List<Slide> {
        val lines = unwrapFence(markdown.lines())
        val chunks = if (lines.any(::isDivider)) {
            splitAt(lines, ::isDivider)
        } else {
            // A model that ignored the divider rule: every heading starts a slide.
            splitBefore(lines, ::isHeading)
        }
        return chunks.mapNotNull(::parseSlide)
    }

    private fun parseSlide(lines: List<String>): Slide? {
        val headingIndex = lines.indexOfFirst(::isHeading)
        val title = if (headingIndex >= 0) lines[headingIndex].trim().trimStart('#').trim() else ""
        val body = lines.filterIndexed { index, line -> index != headingIndex && line.isNotBlank() }
        if (title.isEmpty() && body.isEmpty()) return null

        // MarkdownParser reads the first line as the title of a document; a stand-in title keeps
        // every line of the body a content step.
        val content = MarkdownParser.parse(listOf("# $title") + body)
            .drop(1)
            .map { it.toModel() }
        return Slide(title = title, content = content)
    }

    /** A line of three or more dashes and nothing else. */
    private fun isDivider(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= 3 && trimmed.all { it == '-' }
    }

    private fun isHeading(line: String): Boolean = line.trim().startsWith("#")

    /** Models often wrap the whole answer in a ```markdown fence; without this every line would be code. */
    private fun unwrapFence(lines: List<String>): List<String> {
        val trimmed = lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        if (trimmed.size < 2) return trimmed
        val first = trimmed.first().trim()
        val last = trimmed.last().trim()
        return if (first.startsWith("```") && last == "```") trimmed.drop(1).dropLast(1) else trimmed
    }

    private fun splitAt(lines: List<String>, isSeparator: (String) -> Boolean): List<List<String>> {
        val chunks = mutableListOf(mutableListOf<String>())
        for (line in lines) {
            if (isSeparator(line)) chunks.add(mutableListOf()) else chunks.last().add(line)
        }
        return chunks
    }

    private fun splitBefore(lines: List<String>, startsChunk: (String) -> Boolean): List<List<String>> {
        val chunks = mutableListOf(mutableListOf<String>())
        for (line in lines) {
            if (startsChunk(line) && chunks.last().isNotEmpty()) chunks.add(mutableListOf(line)) else chunks.last().add(line)
        }
        return chunks
    }
}
