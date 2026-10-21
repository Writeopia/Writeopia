package io.writeopia.sdk.model.action

import io.writeopia.sdk.models.story.StoryStep

/**
 * An action performed in the text editor.
 */
sealed class Action {
    data class EraseStory(val storyStep: StoryStep, val position: Long) : Action()

    data class DeleteStory(val storyStep: StoryStep, val position: Long) : Action()

    data class BulkDelete(val deletedUnits: Map<Long, StoryStep>) : Action()

    data class LineBreak(val storyStep: StoryStep, val position: Long) : Action()

    data class Move(val storyStep: StoryStep, val positionFrom: Long, val positionTo: Long) : Action()

    data class BulkMove(
        val storyStep: List<StoryStep>,
        val positionFrom: Set<Long>,
        val positionTo: Long
    ) : Action()

    data class StoryStateChange(
        val storyStep: StoryStep,
        val position: Long,
        val selectionStart: Int? = null,
        val selectionEnd: Int? = null,
        val preserveFocus: Boolean = false
    ) : Action()

    data class Merge(
        val receiver: StoryStep,
        val sender: StoryStep,
        val positionFrom: Long,
        val positionTo: Long
    ) : Action()
}
