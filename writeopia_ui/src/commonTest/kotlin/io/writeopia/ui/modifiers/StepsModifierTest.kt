package io.writeopia.ui.modifiers

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.model.DrawStory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    // Code Block Tests

    @Test
    fun `single code block should have position 2 (single)`() {
        val input = listOf(
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "code line")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, -1)
        val codeBlocks = result.filter { it.storyStep.type == StoryTypes.CODE_BLOCK.type }

        assertEquals(1, codeBlocks.size)
        assertEquals(2, codeBlocks[0].extraInfo["codeBlockPosition"])
        assertEquals(1, codeBlocks[0].extraInfo["codeBlockLineNumber"])
    }

    @Test
    fun `two consecutive code blocks should have positions -1 and 1`() {
        val input = listOf(
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 2")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, -1)
        val codeBlocks = result.filter { it.storyStep.type == StoryTypes.CODE_BLOCK.type }

        assertEquals(2, codeBlocks.size)
        assertEquals(-1, codeBlocks[0].extraInfo["codeBlockPosition"]) // first
        assertEquals(1, codeBlocks[1].extraInfo["codeBlockPosition"])  // last
    }

    @Test
    fun `three consecutive code blocks should have positions -1, 0, and 1`() {
        val input = listOf(
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 2"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 3")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, -1)
        val codeBlocks = result.filter { it.storyStep.type == StoryTypes.CODE_BLOCK.type }

        assertEquals(3, codeBlocks.size)
        assertEquals(-1, codeBlocks[0].extraInfo["codeBlockPosition"]) // first
        assertEquals(0, codeBlocks[1].extraInfo["codeBlockPosition"])  // middle
        assertEquals(1, codeBlocks[2].extraInfo["codeBlockPosition"])  // last
    }

    @Test
    fun `code blocks should have correct line numbers`() {
        val input = listOf(
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 2"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 3")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, -1)
        val codeBlocks = result.filter { it.storyStep.type == StoryTypes.CODE_BLOCK.type }

        assertEquals(3, codeBlocks.size)
        assertEquals(1, codeBlocks[0].extraInfo["codeBlockLineNumber"])
        assertEquals(2, codeBlocks[1].extraInfo["codeBlockLineNumber"])
        assertEquals(3, codeBlocks[2].extraInfo["codeBlockLineNumber"])
    }

    @Test
    fun `spaces between consecutive code blocks should be marked as inside code block`() {
        val input = listOf(
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 2"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "line 3")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        // Use Int.MIN_VALUE to indicate "no drag" - using -1 would cause the first space to be ON_DRAG_SPACE
        val result = StepsModifier.modify(input, Int.MIN_VALUE)

        // Find spaces between code blocks (both SPACE and ON_DRAG_SPACE types)
        val spacesInsideCodeBlock = result.filter {
            (it.storyStep.type == StoryTypes.SPACE.type ||
                it.storyStep.type == StoryTypes.ON_DRAG_SPACE.type) &&
                it.extraInfo[StepsModifier.IS_INSIDE_CODE_BLOCK_KEY] == true
        }

        // There should be 2 spaces between 3 code blocks, both marked as inside code block
        assertEquals(2, spacesInsideCodeBlock.size)

        // Verify that the first space (before any code block) is NOT marked as inside code block
        val firstSpace = result.firstOrNull {
            it.storyStep.type == StoryTypes.SPACE.type ||
                it.storyStep.type == StoryTypes.ON_DRAG_SPACE.type
        }
        assertNotEquals(
            firstSpace?.extraInfo?.get(StepsModifier.IS_INSIDE_CODE_BLOCK_KEY),
            true,
            "First space should not be inside code block"
        )
    }

    @Test
    fun `two separate code blocks should each have their own line numbers`() {
        val input = listOf(
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "block1 line 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "block1 line 2"),
            StoryStep(type = StoryTypes.TEXT.type, text = "normal text"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "block2 line 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "block2 line 2")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, -1)
        val codeBlocks = result.filter { it.storyStep.type == StoryTypes.CODE_BLOCK.type }

        assertEquals(4, codeBlocks.size)

        // First block line numbers
        assertEquals(1, codeBlocks[0].extraInfo["codeBlockLineNumber"])
        assertEquals(2, codeBlocks[1].extraInfo["codeBlockLineNumber"])

        // Second block line numbers (should reset to 1)
        assertEquals(1, codeBlocks[2].extraInfo["codeBlockLineNumber"])
        assertEquals(2, codeBlocks[3].extraInfo["codeBlockLineNumber"])
    }

    @Test
    fun `two separate code blocks should each have their own positions`() {
        val input = listOf(
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "block1 line 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "block1 line 2"),
            StoryStep(type = StoryTypes.TEXT.type, text = "normal text"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "block2 line 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "block2 line 2")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, -1)
        val codeBlocks = result.filter { it.storyStep.type == StoryTypes.CODE_BLOCK.type }

        assertEquals(4, codeBlocks.size)

        // First block positions
        assertEquals(-1, codeBlocks[0].extraInfo["codeBlockPosition"]) // first
        assertEquals(1, codeBlocks[1].extraInfo["codeBlockPosition"])  // last

        // Second block positions (should reset)
        assertEquals(-1, codeBlocks[2].extraInfo["codeBlockPosition"]) // first
        assertEquals(1, codeBlocks[3].extraInfo["codeBlockPosition"])  // last
    }

    @Test
    fun `text items should have spaces between them`() {
        val input = listOf(
            StoryStep(type = StoryTypes.TEXT.type, text = "text 1"),
            StoryStep(type = StoryTypes.TEXT.type, text = "text 2")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        val result = StepsModifier.modify(input, -1)
        val types = result.map { it.storyStep.type }

        // Text items should have spaces between them
        val textIndices = types.mapIndexedNotNull { index, type ->
            if (type == StoryTypes.TEXT.type) index else null
        }

        assertEquals(textIndices.size, 2)
        assertTrue(
            textIndices[1] - textIndices[0] > 1,
            "There should be a space between text items"
        )
    }

    @Test
    fun `mixed content with code blocks should have correct structure`() {
        val input = listOf(
            StoryStep(type = StoryTypes.TEXT.type, text = "intro"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "code 1"),
            StoryStep(type = StoryTypes.CODE_BLOCK.type, text = "code 2"),
            StoryStep(type = StoryTypes.TEXT.type, text = "outro")
        ).mapIndexed { index, step ->
            DrawStory(step, index)
        }

        // Use Int.MIN_VALUE to indicate "no drag" - using -1 would cause the first space to be ON_DRAG_SPACE
        val result = StepsModifier.modify(input, Int.MIN_VALUE)

        // Get non-space items
        val contentItems = result.filter {
            it.storyStep.type != StoryTypes.SPACE.type &&
                it.storyStep.type != StoryTypes.LAST_SPACE.type &&
                it.storyStep.type != StoryTypes.ON_DRAG_SPACE.type
        }

        assertEquals(4, contentItems.size)
        assertEquals(StoryTypes.TEXT.type, contentItems[0].storyStep.type)
        assertEquals(StoryTypes.CODE_BLOCK.type, contentItems[1].storyStep.type)
        assertEquals(StoryTypes.CODE_BLOCK.type, contentItems[2].storyStep.type)
        assertEquals(StoryTypes.TEXT.type, contentItems[3].storyStep.type)

        // Space between code blocks should be marked as inside code block
        val spaceBetweenCodeBlocks = result.filter {
            (it.storyStep.type == StoryTypes.SPACE.type ||
                it.storyStep.type == StoryTypes.ON_DRAG_SPACE.type) &&
                it.extraInfo[StepsModifier.IS_INSIDE_CODE_BLOCK_KEY] == true
        }
        assertEquals(1, spaceBetweenCodeBlocks.size)

        // Spaces NOT between code blocks should not be marked
        val regularSpaces = result.filter {
            (it.storyStep.type == StoryTypes.SPACE.type ||
                it.storyStep.type == StoryTypes.ON_DRAG_SPACE.type) &&
                it.extraInfo[StepsModifier.IS_INSIDE_CODE_BLOCK_KEY] != true
        }
        // Should have spaces: before intro, between intro and code1, between code2 and outro
        assertEquals(3, regularSpaces.size)
    }
}
