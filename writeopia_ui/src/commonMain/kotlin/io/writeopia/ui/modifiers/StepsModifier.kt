package io.writeopia.ui.modifiers

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.model.DrawStory

object StepsModifier {

    fun modify(stories: List<DrawStory>, dragPosition: Int): List<DrawStory> {
        val space = { StoryStep(type = StoryTypes.SPACE.type, localId = GenerateId.generate()) }
        val onDragSpace = StoryStep(type = StoryTypes.ON_DRAG_SPACE.type, localId = "onDragSpace")
        val lastSpace = StoryStep(type = StoryTypes.LAST_SPACE.type)

        val parsed = stories.foldIndexed(emptyList<DrawStory>()) { index, acc, drawStory ->
            val lastStep = acc.lastOrNull { draw ->
                draw.storyStep.type != StoryTypes.SPACE.type &&
                    draw.storyStep.type != StoryTypes.ON_DRAG_SPACE.type
            }?.storyStep

            val lastTags = lastStep?.tags ?: emptySet()
            val currentTags = drawStory.storyStep.tags
            val newTags = mergeTags(lastTags, currentTags)

            val spaceStory =
                if (index - 1 == dragPosition) onDragSpace else space()

            val spaceDraw = DrawStory(
                storyStep = spaceStory.copy(tags = newTags),
                position = index - 1
            )

            acc + spaceDraw + drawStory
        }

        val lastIndex = parsed.lastIndex
        val fullStory = parsed + DrawStory(storyStep = lastSpace, position = lastIndex)

        val fixedPositions = addPositionToTags(fullStory)
        return fixedPositions
    }

    private fun mergeTags(tags1: Set<TagInfo>, tags2: Set<TagInfo>): Set<TagInfo> {
        val enums1 = tags1.filter { it.tag.hasPosition() }.toSet()
        val enums2 = tags2.filter { it.tag.hasPosition() }.toSet()

        return enums1.intersect(enums2) + tags2.filter { it.tag.isHidden() }
    }

    private fun addPositionToTags(stories: List<DrawStory>): List<DrawStory> {
        val resultList = mutableListOf<DrawStory>()

        val setTagPosition: (Iterable<TagInfo>, Int) -> List<TagInfo> = { tagInfoList, position ->
            tagInfoList
                .map { tagInfo ->
                    if (tagInfo.tag.hasPosition()) tagInfo.copy(position = position) else tagInfo
                }
        }

        val hasPositionTagFn: (DrawStory) -> Boolean = { draw ->
            draw.storyStep.tags.any { it.tag.hasPosition() }
        }

        for (i in 0..stories.lastIndex) {
            val draw = stories[i]
            val tags = draw.storyStep.tags

            if (tags.isNotEmpty()) {
                val hasPositionTag = tags.any { it.tag.hasPosition() }

                val previousTags = if (i > 0) hasPositionTagFn(stories[i - 1]) else false
                val nextTags =
                    if (i < stories.lastIndex) hasPositionTagFn(stories[i + 1]) else false

                val newTags = if (hasPositionTag) {
                    when {
                        i == 0 -> setTagPosition(tags, -1)

                        i == stories.lastIndex -> setTagPosition(tags, 1)

                        previousTags && !nextTags -> setTagPosition(tags, 1)

                        previousTags && nextTags -> setTagPosition(tags, 0)

                        !previousTags && nextTags -> setTagPosition(tags, -1)

                        !previousTags && !nextTags -> setTagPosition(tags, 2)

                        else -> setTagPosition(tags, 2)
                    }
                } else {
                    tags
                }

                val newStep = draw.storyStep.copy(tags = newTags.toSet())
                resultList.add(draw.copy(storyStep = newStep))
            } else {
                resultList.add(draw)
            }
        }

        return resultList
    }
}
