package io.writeopia.ui.spellcheck

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Visual transformation that adds underlines to misspelled words.
 * This transformation preserves existing text spans and adds spell check highlighting on top.
 *
 * Note: Compose's SpanStyle doesn't support separate underline colors. The underline
 * uses the text color. We add a subtle red background to indicate misspellings.
 *
 * @param misspelledRanges List of text ranges that represent misspelled words
 * @param highlightColor The color to use for the background highlight (defaults to light red)
 */
class SpellCheckVisualTransformation(
    private val misspelledRanges: List<IntRange>,
    private val highlightColor: Color = Color.Red.copy(alpha = 0.25f)
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (misspelledRanges.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val transformedText = buildAnnotatedString {
            // First, append the original text with all its existing spans
            append(text)

            // Then add underline spans for misspelled words
            for (range in misspelledRanges) {
                // Ensure range is valid for the text
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(0, text.length)

                if (start < end) {
                    addStyle(
                        style = SpanStyle(
                            background = highlightColor
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }

        // No offset changes since we're just adding visual styling
        return TransformedText(transformedText, OffsetMapping.Identity)
    }
}
