package io.writeopia.sdk.manager

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes

/**
 * This class is responsible to control the focus of the edition of story. (example: If the text
 * of a story is being edited, this story has the current focus.)
 */
class FocusHandler(
    private val isMessageFn: (Int) -> Boolean = { typeNumber ->
        typeNumber == StoryTypes.TEXT.type.number ||
            typeNumber == StoryTypes.TEXT_BOX.type.number ||
            typeNumber == StoryTypes.CHECK_ITEM.type.number ||
            typeNumber == StoryTypes.UNORDERED_LIST_ITEM.type.number
    }
) {

    fun findNextFocus(position: Double, stories: Map<Double, StoryStep>): Double? =
        stories.entries
            .sortedBy { (key, _) -> key }
            .find { (key, storyStep) ->
                key > position && isMessageFn(storyStep.type.number)
            }
            ?.key

    fun findPreviousFocus(position: Double, stories: Map<Double, StoryStep>): Double? =
        stories.entries
            .sortedByDescending { (key, _) -> key }
            .find { (key, storyStep) ->
                key < position && isMessageFn(storyStep.type.number)
            }
            ?.key
}
