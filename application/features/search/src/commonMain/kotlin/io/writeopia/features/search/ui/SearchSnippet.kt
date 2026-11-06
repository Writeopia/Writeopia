package io.writeopia.features.search.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import io.writeopia.sqldelight.fts.FtsQuery

/** The snippet of a search result: the step the query was found in, cut around the first word that matched. */
object SearchSnippet {

    /**
     * [text] cut to about [maxLength] characters around the first occurrence of a word of
     * [query], with an ellipsis on the sides that were cut. The whole text when it is short.
     */
    fun window(text: String, query: String, maxLength: Int = MAX_LENGTH): String {
        val clean = text.replace('\n', ' ').trim()
        if (clean.length <= maxLength) return clean

        val hit = ranges(clean, query).firstOrNull()?.first ?: 0
        val start = (hit - maxLength / 3).coerceAtLeast(0)
        val end = (start + maxLength).coerceAtMost(clean.length)
        val from = if (end == clean.length) (end - maxLength).coerceAtLeast(0) else start

        val prefix = if (from > 0) "…" else ""
        val suffix = if (end < clean.length) "…" else ""
        return prefix + clean.substring(from, end).trim() + suffix
    }

    /** The character ranges of [text] where a word of [query] starts, by prefix and ignoring case. */
    fun ranges(text: String, query: String): List<IntRange> {
        val tokens = FtsQuery.tokens(query)
        if (tokens.isEmpty()) return emptyList()

        val lower = text.lowercase()
        return tokens.flatMap { token ->
            generateSequence(lower.indexOf(token)) { index -> lower.indexOf(token, index + 1) }
                .takeWhile { index -> index >= 0 }
                .map { index -> index until (index + token.length) }
                .toList()
        }.sortedBy { range -> range.first }
    }

    const val MAX_LENGTH = 90
}

/** [text] with the words of [query] in bold and [highlight]. */
@Composable
fun highlightedSnippet(text: String, query: String, highlight: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        SearchSnippet.ranges(text, query).forEach { range ->
            addStyle(
                SpanStyle(color = highlight, fontWeight = FontWeight.Bold),
                range.first,
                (range.last + 1).coerceAtMost(text.length)
            )
        }
    }
