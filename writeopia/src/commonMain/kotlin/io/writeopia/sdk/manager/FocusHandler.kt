package io.writeopia.sdk.manager

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.nextKey
import io.writeopia.sdk.utils.iterables.previousKey

/**
 * Finds the story that should receive the focus after or before a position. It walks from the
 * position through the neighbours, so finding the story next door costs one lookup: O(log n)
 * on a sorted map on JVM.
 */
class FocusHandler(
    private val isMessageFn: (Int) -> Boolean = { typeNumber ->
        typeNumber == StoryTypes.TEXT.type.number ||
            typeNumber == StoryTypes.TEXT_BOX.type.number ||
            typeNumber == StoryTypes.CHECK_ITEM.type.number ||
            typeNumber == StoryTypes.UNORDERED_LIST_ITEM.type.number
    }
) {

    fun findNextFocus(position: String, stories: Map<String, StoryStep>): String? {
        var key = stories.nextKey(position)
        while (key != null) {
            if (stories[key]?.let { story -> isMessageFn(story.type.number) } == true) return key
            key = stories.nextKey(key)
        }
        return null
    }

    fun findPreviousFocus(position: String, stories: Map<String, StoryStep>): String? {
        var key = stories.previousKey(position)
        while (key != null) {
            if (stories[key]?.let { story -> isMessageFn(story.type.number) } == true) return key
            key = stories.previousKey(key)
        }
        return null
    }
}
