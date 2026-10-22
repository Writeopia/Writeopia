package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes

/**
 * The text that goes with a free command: the whole document and the lines selected when the
 * command was typed, if any. The backend cuts the document when it is too long.
 */
data class AiCommandContext(
    val selectedText: String,
    /** The positions of the selected lines, sorted. Empty when nothing is selected. */
    val selectedPositions: List<String>,
    val documentText: String,
) {
    companion object {
        fun build(stories: Map<String, StoryStep>, selectedPositions: Collection<String>): AiCommandContext {
            val selected = selectedPositions.sorted().filter { position ->
                !stories[position]?.text.isNullOrBlank()
            }

            // The title is sent on its own, so it is not part of the document text.
            val documentText = stories.entries
                .filter { (_, step) ->
                    !step.text.isNullOrBlank() && !step.ephemeral && step.type != StoryTypes.TITLE.type
                }
                .sortedBy { (position, _) -> position }
                .joinToString("\n") { (_, step) -> step.text ?: "" }

            return AiCommandContext(
                selectedText = selected.mapNotNull { position -> stories[position]?.text }.joinToString("\n"),
                selectedPositions = selected,
                documentText = documentText,
            )
        }
    }
}
