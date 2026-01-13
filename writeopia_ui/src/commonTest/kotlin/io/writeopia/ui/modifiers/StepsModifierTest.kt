package io.writeopia.ui.modifiers

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.model.DrawStory
import kotlin.test.Test
import kotlin.test.assertEquals

class StepsModifierTest {

    @Test
    fun `tags should be merged correctly - simple`() {
        val input = listOf(
            StoryStep(type = StoryTypes.TEXT.type, tags = setOf(TagInfo(Tag.HIGH_LIGHT_BLOCK))),
            StoryStep(type = StoryTypes.TEXT.type, tags = setOf(TagInfo(Tag.HIGH_LIGHT_BLOCK)))
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, 0)
            .map { it.storyStep.tags }
            .flatten()
        val expected = listOf(
            TagInfo(Tag.HIGH_LIGHT_BLOCK, -1),
            TagInfo(Tag.HIGH_LIGHT_BLOCK, 0),
            TagInfo(Tag.HIGH_LIGHT_BLOCK, 1),
        )

        assertEquals(expected, result)
    }

    @Test
    fun `tags should be merged correctly, with multiple types`() {
        val input = listOf(
            StoryStep(
                text = "Title",
                type = StoryTypes.TEXT.type,
                tags = setOf(
                    TagInfo(Tag.HIGH_LIGHT_BLOCK),
                    TagInfo(Tag.H1),
                )
            ),
            StoryStep(type = StoryTypes.TEXT.type, tags = setOf(TagInfo(Tag.HIGH_LIGHT_BLOCK))),
            StoryStep(type = StoryTypes.TEXT.type, tags = setOf(TagInfo(Tag.HIGH_LIGHT_BLOCK)))
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, 0)
            .map { it.storyStep.tags }
            .flatten()
            .filter { tagInfo ->
                tagInfo.tag == Tag.HIGH_LIGHT_BLOCK
            }
        val expected = listOf(
            TagInfo(Tag.HIGH_LIGHT_BLOCK, -1),
            TagInfo(Tag.HIGH_LIGHT_BLOCK, 0),
            TagInfo(Tag.HIGH_LIGHT_BLOCK, 0),
            TagInfo(Tag.HIGH_LIGHT_BLOCK, 0),
            TagInfo(Tag.HIGH_LIGHT_BLOCK, 1),
        )

        assertEquals(expected, result)
    }
}
