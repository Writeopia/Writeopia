package io.writeopia.ui.modifiers

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.model.DrawStory

object StepsModifier {

    private const val CODE_BLOCK_POSITION_KEY = "codeBlockPosition"

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

            // Skip space between consecutive CODE_BLOCK items
            val lastIsCodeBlock = lastStep?.type == StoryTypes.CODE_BLOCK.type
            val currentIsCodeBlock = drawStory.storyStep.type == StoryTypes.CODE_BLOCK.type
            val skipSpace = lastIsCodeBlock && currentIsCodeBlock

            if (skipSpace) {
                acc + drawStory
            } else {
                val spaceStory =
                    if (index - 1 == dragPosition) onDragSpace else space()

                val spaceDraw = DrawStory(
                    storyStep = spaceStory.copy(tags = newTags),
                    position = index - 1
                )

                acc + spaceDraw + drawStory
            }
        }

        val lastIndex = parsed.lastIndex
        val fullStory = parsed + DrawStory(storyStep = lastSpace, position = lastIndex)

        val fixedPositions = addPositionToTags(fullStory)
        val fixedCodeBlockPositions = addPositionToCodeBlocks(fixedPositions)
        return fixedCodeBlockPositions
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

    private fun addPositionToCodeBlocks(stories: List<DrawStory>): List<DrawStory> {
        val isCodeBlockFn: (DrawStory) -> Boolean = { draw ->
            draw.storyStep.type == StoryTypes.CODE_BLOCK.type
        }

        return stories.mapIndexed { i, draw ->
            if (!isCodeBlockFn(draw)) {
                draw
            } else {
                val previousIsCodeBlock = if (i > 0) isCodeBlockFn(stories[i - 1]) else false
                val nextIsCodeBlock =
                    if (i < stories.lastIndex) isCodeBlockFn(stories[i + 1]) else false

                val position = when {
                    i == 0 -> -1
                    i == stories.lastIndex -> 1
                    previousIsCodeBlock && !nextIsCodeBlock -> 1
                    previousIsCodeBlock && nextIsCodeBlock -> 0
                    !previousIsCodeBlock && nextIsCodeBlock -> -1
                    !previousIsCodeBlock && !nextIsCodeBlock -> 2
                    else -> 2
                }

                draw.copy(extraInfo = draw.extraInfo + (CODE_BLOCK_POSITION_KEY to position))
            }
        }
    }
}
