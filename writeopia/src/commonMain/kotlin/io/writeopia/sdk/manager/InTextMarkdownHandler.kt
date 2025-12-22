package io.writeopia.sdk.manager

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep

object InTextMarkdownHandler {

    // Bold: Matches exactly **text**
    private val BOLD_REGEX = Regex("""\*\*(?!\*)(.*?)\*\*""")

    // Italic: Matches *text* but ensures the boundaries are not double asterisks
    // (?<!\*) means "not preceded by *"
    // (?!\*) means "not followed by *"
    private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.*?)(?<!\*)\*(?!\*)""")

    fun handleMarkdown(storyStep: StoryStep): StoryStep {
        val originalText = storyStep.text ?: return storyStep

        val newSpans = mutableSetOf<SpanInfo>()
        var processedText = originalText

        // Order matters: Process bold first to "clean" those markers
        // before the italic logic runs.
        processedText = processPattern(processedText, BOLD_REGEX, Span.BOLD, newSpans)
        processedText = processPattern(processedText, ITALIC_REGEX, Span.ITALIC, newSpans)

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
}
