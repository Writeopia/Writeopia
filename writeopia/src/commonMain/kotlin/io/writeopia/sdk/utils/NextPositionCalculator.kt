package io.writeopia.sdk.utils

import io.writeopia.sdk.models.story.StoryStep

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
     * @param stories The map of positions to StorySteps
     * @return A new map with nextPosition and previousPosition populated for each StoryStep
     */
    fun calculate(stories: Map<Double, StoryStep>): Map<Double, StoryStep> {
        if (stories.isEmpty()) return stories

        val sortedPositions = stories.keys.sorted()

        return stories.mapValues { (position, storyStep) ->
            val currentIndex = sortedPositions.indexOf(position)
            val nextPosition = if (currentIndex < sortedPositions.lastIndex) {
                sortedPositions[currentIndex + 1]
            } else {
                null
            }
            val previousPosition = if (currentIndex > 0) {
                sortedPositions[currentIndex - 1]
            } else {
                null
            }
            storyStep.copy(nextPosition = nextPosition, previousPosition = previousPosition)
        }
    }
}
