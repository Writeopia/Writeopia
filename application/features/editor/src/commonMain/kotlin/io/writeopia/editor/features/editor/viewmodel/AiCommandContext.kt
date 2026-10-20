package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes

/**
 * The text that goes with a free command: the target (the selected lines or the paragraph at the
 * cursor) and the document around it. A short document goes whole; a long one is cut to the
 * [WINDOW] paragraphs before and after the target, which is never cut. Without a target the
 * command is about the whole document, which goes whole as [textBefore].
 */
data class AiCommandContext(
    val targetText: String,
    /** The positions of the target, sorted. Empty when the command is about the whole document. */
    val targetPositions: List<Double>,
    val textBefore: String,
    val textAfter: String,
) {
    companion object {
        /** A document with this many paragraphs or fewer goes whole. */
        const val WHOLE_DOCUMENT_LIMIT = 30

        /** The paragraphs kept on each side of the target of a longer document. */
        const val WINDOW = 10

        fun build(stories: Map<Double, StoryStep>, targetPositions: Collection<Double>): AiCommandContext {
            val targets = targetPositions.sorted().filter { position ->
                !stories[position]?.text.isNullOrBlank()
            }
            val targetText = targets.mapNotNull { position -> stories[position]?.text }.joinToString("\n")

            // The title is sent on its own, so it is not part of the text around the target.
            val paragraphs = stories.entries
                .filter { (_, step) ->
                    !step.text.isNullOrBlank() && !step.ephemeral && step.type != StoryTypes.TITLE.type
                }
                .sortedBy { (position, _) -> position }

            val first = targets.firstOrNull()
            val last = targets.lastOrNull()
            val before = if (first == null) paragraphs else paragraphs.filter { (position, _) -> position < first }
            val after = if (last == null) emptyList() else paragraphs.filter { (position, _) -> position > last }
            val cut = first != null && paragraphs.size > WHOLE_DOCUMENT_LIMIT

            return AiCommandContext(
                targetText = targetText,
                targetPositions = targets,
                textBefore = (if (cut) before.takeLast(WINDOW) else before).joinText(),
                textAfter = (if (cut) after.take(WINDOW) else after).joinText(),
            )
        }

        private fun List<Map.Entry<Double, StoryStep>>.joinText(): String =
            joinToString("\n") { (_, step) -> step.text ?: "" }
    }
}
