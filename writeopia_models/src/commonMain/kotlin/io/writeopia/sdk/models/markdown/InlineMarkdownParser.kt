package io.writeopia.sdk.models.markdown

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep

/**
 * Shared inline markdown parser that converts markdown syntax (bold, italic, URLs)
 * into SpanInfo objects. This can be used by both the Editor and Backend.
 */
object InlineMarkdownParser {

    // Bold: Matches exactly **text**
    private val BOLD_REGEX = Regex("""\*\*(?!\*)(.*?)\*\*""")

    // Italic: Matches *text* but ensures the boundaries are not double asterisks
    // (?<!\*) means "not preceded by *"
    // (?!\*) means "not followed by *"
    private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.*?)(?<!\*)\*(?!\*)""")

    // URL: Matches http:// or https:// followed by non-whitespace characters
    private val URL_REGEX = Regex("""(https?://[^\s]+)""")

    /**
     * Parses a StoryStep's text for markdown syntax and converts markers to spans.
     *
     * Processes in order:
     * 1. Bold (**text**) - removes markers and adds BOLD span
     * 2. Italic (*text*) - removes markers and adds ITALIC span
     * 3. URLs (http:// or https://) - adds LINK span without modifying text
     *
     * @param storyStep The StoryStep to parse
     * @return A new StoryStep with parsed spans, or the original if no markdown was found
     */
    fun parseMarkdown(storyStep: StoryStep): StoryStep {
        val originalText = storyStep.text ?: return storyStep

        val newSpans = mutableSetOf<SpanInfo>()
        var processedText = originalText

        // Order matters: Process bold first to "clean" those markers
        // before the italic logic runs.
        processedText = processPattern(processedText, BOLD_REGEX, Span.BOLD, newSpans)
        processedText = processPattern(processedText, ITALIC_REGEX, Span.ITALIC, newSpans)

        // Process URLs - they don't modify text, just add spans
        processUrlPattern(processedText, URL_REGEX, newSpans)

        return if (newSpans.isNotEmpty()) {
            storyStep.copy(
                text = processedText,
                spans = storyStep.spans + newSpans,
                localId = if (newSpans.isNotEmpty()) GenerateId.generate() else storyStep.localId
            )
        } else {
            storyStep
        }
    }

    private fun processPattern(
        text: String,
        regex: Regex,
        spanType: Span,
        spanSet: MutableSet<SpanInfo>
    ): String {
        var currentText = text
        var match = regex.find(currentText)

        while (match != null) {
            val fullMatchRange = match.range
            val content = match.groupValues[1]

            // Check to ensure there is actually content inside the tags
            if (content.isNotEmpty()) {
                val spanInfo = SpanInfo.create(
                    start = fullMatchRange.first,
                    end = fullMatchRange.first + content.length,
                    span = spanType
                )
                spanSet.add(spanInfo)
                currentText = currentText.replaceRange(fullMatchRange, content)
            } else {
                // If it's just **, we move past it to avoid infinite loops
                match = regex.find(currentText, fullMatchRange.first + 1)
                continue
            }

            match = regex.find(currentText)
        }
        return currentText
    }

    private fun processUrlPattern(
        text: String,
        regex: Regex,
        spanSet: MutableSet<SpanInfo>
    ) {
        var match = regex.find(text)

        while (match != null) {
            val url = match.value
            val range = match.range

            if (url.isNotEmpty()) {
                spanSet.add(
                    SpanInfo.create(
                        start = range.first,
                        end = range.last + 1,
                        span = Span.LINK,
                        extra = url
                    )
                )
            }

            match = regex.find(text, range.last + 1)
        }
    }
}
