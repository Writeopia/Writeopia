package io.writeopia.claudecode.markdown

import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo

/**
 * Turns the Markdown that Claude Code sends into blocks of the editor, one [StoryStep] per
 * block, in order and without positions (the writer places them).
 *
 * Mirrored by `LiveMarkdown.swift` in the native app: both must read the same Markdown into the
 * same blocks and spans.
 *
 * Blocks: `#`..`######` headings (H1..H4, deeper ones are H4), `- [ ]`/`- [x]` checks, `-`/`*`/`+`
 * bullets, `>` quotes (highlighted blocks), fenced code (one code block per line), `---` dividers,
 * a line holding only `![alt](url)` (an image), `$$latex$$` (an equation, also over several
 * lines); anything else is a paragraph. Blank lines only separate blocks.
 *
 * Inline: `**bold**`, `*italic*`/`_italic_`, `==highlight==`, `[text](url)`, bare `http(s)://`
 * addresses (links), `` `code` `` (kept as plain text) and `\` escapes.
 */
object LiveMarkdown {

    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val check = Regex("""^[-*+]\s+\[([ xX])]\s+(.*)$""")
    private val bullet = Regex("""^[-*+]\s+(.*)$""")
    private val divider = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    private val image = Regex("""^!\[([^\]]*)]\((\S+?)\)$""")
    private val quote = Regex("""^>\s?(.*)$""")

    private const val EQUATION = "$$"

    fun blocks(markdown: String): List<StoryStep> {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val result = mutableListOf<StoryStep>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            val fence = fenceOf(trimmed)
            if (fence != null) {
                index++
                while (index < lines.size && !closesFence(lines[index].trim(), fence)) {
                    result.add(StoryStep(type = StoryTypes.CODE_BLOCK.type, text = lines[index].trimEnd()))
                    index++
                }
                index++ // the closing fence
                continue
            }

            if (trimmed.startsWith(EQUATION)) {
                val single = trimmed.length > EQUATION.length * 2 && trimmed.endsWith(EQUATION)
                if (single) {
                    result.add(equation(trimmed.removePrefix(EQUATION).removeSuffix(EQUATION)))
                    index++
                    continue
                }

                val closing = (index + 1 until lines.size).firstOrNull { lines[it].trim().endsWith(EQUATION) }
                if (closing != null) {
                    val latex = buildList {
                        add(trimmed.removePrefix(EQUATION))
                        (index + 1 until closing).forEach { add(lines[it].trim()) }
                        add(lines[closing].trim().removeSuffix(EQUATION))
                    }.joinToString(" ") { it.trim() }
                    result.add(equation(latex))
                    index = closing + 1
                    continue
                }
            }

            block(trimmed)?.let(result::add)
            index++
        }

        return result
    }

    private fun equation(latex: String) = StoryStep(type = StoryTypes.EQUATION.type, text = latex.trim())

    private fun block(trimmed: String): StoryStep? {
        if (trimmed.isEmpty()) return null

        heading.matchEntire(trimmed)?.let { match ->
            val tag = when (match.groupValues[1].length) {
                1 -> Tag.H1
                2 -> Tag.H2
                3 -> Tag.H3
                else -> Tag.H4
            }
            return text(StoryTypes.TEXT, match.groupValues[2], setOf(TagInfo(tag)))
        }

        if (divider.matches(trimmed)) return StoryStep(type = StoryTypes.DIVIDER.type)

        check.matchEntire(trimmed)?.let { match ->
            return text(StoryTypes.CHECK_ITEM, match.groupValues[2])
                .copy(checked = match.groupValues[1] != " ")
        }

        bullet.matchEntire(trimmed)?.let { match ->
            return text(StoryTypes.UNORDERED_LIST_ITEM, match.groupValues[1])
        }

        image.matchEntire(trimmed)?.let { match ->
            return StoryStep(
                type = StoryTypes.IMAGE.type,
                text = match.groupValues[1].ifBlank { null },
                url = match.groupValues[2],
            )
        }

        quote.matchEntire(trimmed)?.let { match ->
            return text(StoryTypes.TEXT, match.groupValues[1], setOf(TagInfo(Tag.HIGH_LIGHT_BLOCK)))
        }

        return text(StoryTypes.TEXT, trimmed)
    }

    private fun text(type: StoryTypes, markdown: String, tags: Set<TagInfo> = emptySet()): StoryStep {
        val (text, spans) = inline(markdown.trim())
        return StoryStep(type = type.type, text = text, tags = tags, spans = spans.toSet())
    }

    private class Fence(val char: Char, val length: Int)

    private fun fenceOf(trimmed: String): Fence? {
        val char = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val length = trimmed.takeWhile { it == char }.length
        return if (length >= 3) Fence(char, length) else null
    }

    private fun closesFence(trimmed: String, fence: Fence): Boolean =
        trimmed.length >= fence.length && trimmed.all { it == fence.char }

    /** The text without the inline markers, and the spans they meant. Offsets are UTF-16. */
    fun inline(markdown: String): Pair<String, List<SpanInfo>> {
        val out = StringBuilder()
        val spans = mutableListOf<SpanInfo>()
        val open = mutableMapOf<String, Int>()
        var i = 0

        fun toggle(marker: String, span: Span) {
            val start = open.remove(marker)
            if (start != null) {
                if (out.length > start) spans.add(SpanInfo.create(start, out.length, span))
            } else {
                open[marker] = out.length
            }
        }

        fun closesLater(marker: String, from: Int): Boolean {
            if (marker.length > 1) return markdown.indexOf(marker, from) >= 0

            // A single `*` closes unless it is a `**` (a run of exactly two); `***` holds one.
            var at = markdown.indexOf(marker, from)
            while (at >= 0) {
                var end = at
                while (end < markdown.length && markdown[end] == marker[0]) end++
                if (end - at != 2) return true
                at = markdown.indexOf(marker, end)
            }
            return false
        }

        while (i < markdown.length) {
            val c = markdown[i]
            val next = markdown.getOrNull(i + 1)
            val previous = markdown.getOrNull(i - 1)

            when {
                c == '\\' && next != null && next in "\\*_=[]()`#>-+!~" -> {
                    out.append(next)
                    i += 2
                }

                markdown.startsWith("**", i) -> {
                    if ("**" in open || (charAt(markdown, i + 2)?.isWhitespace() == false && closesLater("**", i + 2))) {
                        toggle("**", Span.BOLD)
                    } else {
                        out.append("**")
                    }
                    i += 2
                }

                markdown.startsWith("==", i) -> {
                    if ("==" in open || (charAt(markdown, i + 2)?.isWhitespace() == false && closesLater("==", i + 2))) {
                        toggle("==", Span.HIGHLIGHT_YELLOW)
                    } else {
                        out.append("==")
                    }
                    i += 2
                }

                c == '*' || c == '_' -> {
                    val marker = c.toString()
                    val opens = next != null &&
                        !next.isWhitespace() &&
                        (c == '*' || previous == null || !previous.isLetterOrDigit()) &&
                        closesLater(marker, i + 1)
                    val closes = marker in open &&
                        (c == '*' || next == null || !next.isLetterOrDigit())
                    if (closes || (marker !in open && opens)) {
                        toggle(marker, Span.ITALIC)
                    } else {
                        out.append(c)
                    }
                    i++
                }

                c == '`' -> {
                    val close = markdown.indexOf('`', i + 1)
                    if (close > i) {
                        out.append(markdown, i + 1, close)
                        i = close + 1
                    } else {
                        out.append(c)
                        i++
                    }
                }

                c == '[' -> {
                    val link = linkAt(markdown, i)
                    if (link != null) {
                        val start = out.length
                        out.append(link.label)
                        if (link.label.isNotEmpty()) {
                            spans.add(SpanInfo.create(start, out.length, Span.LINK, link.url))
                        }
                        i = link.end
                    } else {
                        out.append(c)
                        i++
                    }
                }

                (markdown.startsWith("https://", i) || markdown.startsWith("http://", i)) &&
                    (previous == null || previous.isWhitespace() || previous == '(') -> {
                    var end = i
                    while (end < markdown.length && !markdown[end].isWhitespace()) end++
                    while (end > i && markdown[end - 1] in ".,;:!?)") end--
                    val url = markdown.substring(i, end)
                    val start = out.length
                    out.append(url)
                    spans.add(SpanInfo.create(start, out.length, Span.LINK, url))
                    i = end
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }

        return out.toString() to spans
    }

    private fun charAt(text: String, index: Int): Char? = text.getOrNull(index)

    private class Link(val label: String, val url: String, val end: Int)

    private fun linkAt(markdown: String, start: Int): Link? {
        val closeLabel = markdown.indexOf("](", start + 1).takeIf { it > start } ?: return null
        if (markdown.substring(start + 1, closeLabel).contains('\n')) return null
        val closeUrl = markdown.indexOf(')', closeLabel + 2).takeIf { it > closeLabel + 2 } ?: return null
        val url = markdown.substring(closeLabel + 2, closeUrl)
        if (url.any { it.isWhitespace() }) return null
        return Link(markdown.substring(start + 1, closeLabel), url, closeUrl + 1)
    }
}
