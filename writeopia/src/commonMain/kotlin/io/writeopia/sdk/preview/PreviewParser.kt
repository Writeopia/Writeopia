package io.writeopia.sdk.preview

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes

/**
 * Parser of wanted previous. This class provides a default logic to filter the desired
 * [StoryType]. It already comes with some default types.
 *
 * @param acceptedTypes Set<Int> The number of the accepted types.
 */
class PreviewParser(
    private val acceptedTypes: Set<Int> = defaultTypes()
) {

    /**
     * Returns the filtered List of StoryStep
     */
    fun preview(stories: Iterable<StoryStep>, maxSize: Int = 10): List<StoryStep> {
        var acc = 0

        val result = stories.asSequence()
            .filter { storyStep -> acceptedTypes.contains(storyStep.type.number) }
            .takeWhile { acc++ < maxSize }
            .toList()

        return if (result.size == 2 &&
            result.first().type.number == StoryTypes.TITLE.type.number &&
            (result.first().text ?: "").isEmpty()
        ) {
            result.drop(1)
        } else {
            result
        }
    }
}

private fun defaultTypes() = setOf(
    StoryTypes.TITLE.type.number,
    StoryTypes.TEXT.type.number,
    StoryTypes.CHECK_ITEM.type.number,
    StoryTypes.UNORDERED_LIST_ITEM.type.number,
    StoryTypes.IMAGE.type.number,
    StoryTypes.AI_ANSWER.type.number,
    StoryTypes.DOCUMENT_LINK.type.number
)
