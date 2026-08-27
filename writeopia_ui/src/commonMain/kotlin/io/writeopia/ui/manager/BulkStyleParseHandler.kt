package io.writeopia.ui.manager

import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.models.markdown.InlineMarkdownParser
import io.writeopia.sdk.models.story.StoryStep

/**
 * Handles bulk parsing of markdown styles when accepting AI responses or pasting content.
 * This includes both block-level markdown (headings, lists, etc.) and inline markdown (bold, italic, URLs).
 */
class BulkStyleParseHandler {

    /**
     * Processes markdown in all steps from a LastEdit.
     *
     * @param lastEdit The LastEdit containing steps to process
     * @param currentStories The current stories map (used after command processing)
     * @param onCommandProcess Callback to process block-level commands. This is called for each step
     *                         and should invoke commandHandler.handleCommand internally.
     * @return A [ParseResult] containing the updated stories and lastEdit, or null if no processing was needed
     */
    fun processMarkdown(
        lastEdit: LastEdit,
        currentStories: () -> Map<Double, StoryStep>,
        onCommandProcess: (position: Double, step: StoryStep, text: String) -> Unit
    ): ParseResult? {
        val stepsToProcess = extractStepsToProcess(lastEdit)
        if (stepsToProcess.isEmpty()) return null

        val positionsToTrack = stepsToProcess.map { it.first }.toSet()

        // Process block-level commands (###, -, [], etc.)
        stepsToProcess.forEach { (pos, step) ->
            val text = step.text
            if (text != null) {
                onCommandProcess(pos, step, text)
            }
        }

        // Apply inline markdown parsing (bold, italic, URLs)
        return applyInlineMarkdown(positionsToTrack, currentStories())
    }

    private fun extractStepsToProcess(lastEdit: LastEdit): List<Pair<Double, StoryStep>> =
        when (lastEdit) {
            is LastEdit.BulkEdition -> lastEdit.steps
            is LastEdit.LineBreakEdition -> listOf(
                lastEdit.originalStep,
                lastEdit.newStep
            )
            else -> emptyList()
        }

    private fun applyInlineMarkdown(
        positionsToTrack: Set<Double>,
        stories: Map<Double, StoryStep>
    ): ParseResult? {
        if (positionsToTrack.isEmpty()) return null

        val parsedSteps = positionsToTrack.mapNotNull { pos ->
            stories[pos]?.let { step ->
                pos to InlineMarkdownParser.parseMarkdown(step)
            }
        }

        val hasChanges = parsedSteps.any { (pos, step) ->
            stories[pos] !== step
        }

        val updatedStories = if (hasChanges) {
            stories + parsedSteps.toMap()
        } else {
            stories
        }

        val newLastEdit = if (parsedSteps.isNotEmpty()) {
            LastEdit.BulkEdition(parsedSteps)
        } else {
            null
        }

        return ParseResult(updatedStories, newLastEdit)
    }

    /**
     * Result of markdown parsing containing the updated stories and lastEdit.
     */
    data class ParseResult(
        val stories: Map<Double, StoryStep>,
        val lastEdit: LastEdit?
    )
}
