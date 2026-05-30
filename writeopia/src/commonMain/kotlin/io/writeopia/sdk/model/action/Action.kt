package io.writeopia.sdk.model.action

import io.writeopia.sdk.models.story.StoryStep

/**
 * An action performed in the text editor.
 */
sealed class Action {
    data class EraseStory(val storyStep: StoryStep, val position: Double) : Action()

    data class DeleteStory(val storyStep: StoryStep, val position: Double) : Action()

    data class BulkDelete(val deletedUnits: Map<Double, StoryStep>) : Action()

    data class LineBreak(val storyStep: StoryStep, val position: Double) : Action()

    data class Move(val storyStep: StoryStep, val positionFrom: Double, val positionTo: Double) : Action()

    data class BulkMove(
        val storyStep: List<StoryStep>,
        val positionFrom: Set<Double>,
        val positionTo: Double
    ) : Action()

    data class StoryStateChange(
        val storyStep: StoryStep,
        val position: Double,
        val selectionStart: Int? = null,
        val selectionEnd: Int? = null
    ) : Action()

    data class Merge(
        val receiver: StoryStep,
        val sender: StoryStep,
        val positionFrom: Double,
        val positionTo: Double
    ) : Action()
}
