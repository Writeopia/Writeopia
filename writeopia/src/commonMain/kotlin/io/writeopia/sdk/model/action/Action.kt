package io.writeopia.sdk.model.action

import io.writeopia.sdk.models.story.StoryStep

/**
 * An action performed in the text editor.
 */
sealed class Action {
    data class EraseStory(val storyStep: StoryStep, val position: String) : Action()

    data class DeleteStory(val storyStep: StoryStep, val position: String) : Action()

    data class BulkDelete(val deletedUnits: Map<String, StoryStep>) : Action()

    data class LineBreak(val storyStep: StoryStep, val position: String) : Action()

    data class Move(val storyStep: StoryStep, val positionFrom: String, val positionTo: String) : Action()

    data class BulkMove(
        val storyStep: List<StoryStep>,
        val positionFrom: Set<String>,
        val positionTo: String
    ) : Action()

    data class StoryStateChange(
        val storyStep: StoryStep,
        val position: String,
        val selectionStart: Int? = null,
        val selectionEnd: Int? = null,
        val preserveFocus: Boolean = false
    ) : Action()

    data class Merge(
        val receiver: StoryStep,
        val sender: StoryStep,
        val positionFrom: String,
        val positionTo: String
    ) : Action()
}
