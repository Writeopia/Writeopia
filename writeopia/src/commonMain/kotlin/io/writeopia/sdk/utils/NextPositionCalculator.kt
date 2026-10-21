package io.writeopia.sdk.utils

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.utils.collections.higherKeyOrNull
import io.writeopia.sdk.utils.collections.lowerKeyOrNull
import io.writeopia.sdk.utils.collections.sortedMutableMapOf

/**
 * Calculates and populates the nextPosition and previousPosition fields for each StoryStep
 * in a document. These values represent the positions of adjacent StorySteps in document order,
 * which is useful for efficiently calculating intermediate positions when inserting new content.
 */
object NextPositionCalculator {

    /**
     * Populates the nextPosition and previousPosition fields for each StoryStep in the given map.
     * The first StoryStep will have previousPosition set to null.
     * The last StoryStep will have nextPosition set to null.
     *
     * @param stories The map of positions to StorySteps, in any order
     * @return A new map, sorted by position, with nextPosition and previousPosition populated
     * for each StoryStep
     */
    fun calculate(stories: Map<Long, StoryStep>): Map<Long, StoryStep> {
        if (stories.isEmpty()) return stories

        val sortedPositions = stories.keys.sorted()
        val result = sortedMutableMapOf<Long, StoryStep>()

        sortedPositions.forEachIndexed { index, position ->
            val storyStep = stories.getValue(position)
            val nextPosition = sortedPositions.getOrNull(index + 1)
            val previousPosition = if (index > 0) sortedPositions[index - 1] else null

            result[position] = if (
                storyStep.nextPosition == nextPosition && storyStep.previousPosition == previousPosition
            ) {
                storyStep
            } else {
                storyStep.copy(nextPosition = nextPosition, previousPosition = previousPosition)
            }
        }

        return result
    }

    /**
     * Relinks the step at [position] and its two neighbours, after one step was added or
     * removed at [position]. No other step is touched: O(log n) on a sorted map on JVM.
     *
     * @param stories The sorted map, changed in place
     * @return [stories]
     */
    fun relinkAround(stories: MutableMap<Long, StoryStep>, position: Long): MutableMap<Long, StoryStep> {
        val previous = stories.lowerKeyOrNull(position)
        val next = stories.higherKeyOrNull(position)
        val present = stories.containsKey(position)

        if (present) {
            stories.relinkPrevious(position, previous)
            stories.relinkNext(position, next)
        }
        if (previous != null) {
            stories.relinkNext(previous, if (present) position else next)
        }
        if (next != null) {
            stories.relinkPrevious(next, if (present) position else previous)
        }

        return stories
    }

    private fun MutableMap<Long, StoryStep>.relinkNext(position: Long, nextPosition: Long?) {
        val step = this[position] ?: return
        if (step.nextPosition != nextPosition) this[position] = step.copy(nextPosition = nextPosition)
    }

    private fun MutableMap<Long, StoryStep>.relinkPrevious(position: Long, previousPosition: Long?) {
        val step = this[position] ?: return
        if (step.previousPosition != previousPosition) this[position] = step.copy(previousPosition = previousPosition)
    }
}
