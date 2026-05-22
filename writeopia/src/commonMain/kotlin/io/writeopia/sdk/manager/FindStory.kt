package io.writeopia.sdk.manager

import io.writeopia.sdk.models.story.StoryStep

/**
 * Helper object to find StoryUnits inside a the List<Story>. This object search in the list and
 * also in GroupSteps.
 */
object FindStory {

    fun previousFocus(
        storyMap: Map<Double, StoryStep>,
        localPosition: Double,
        focusableTypes: Set<Int>
    ): Double? {
        val sortedKeys = storyMap.keys.sorted()
        val currentIndex = sortedKeys.indexOf(localPosition)

        if (currentIndex <= 0) return null

        for (i in (currentIndex - 1) downTo 0) {
            val position = sortedKeys[i]
            val step = storyMap[position]
            if (step != null && focusableTypes.contains(step.type.number)) {
                return position
            }
        }

        return null
    }
}
