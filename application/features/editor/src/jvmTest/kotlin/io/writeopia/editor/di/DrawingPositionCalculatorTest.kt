package io.writeopia.editor.di

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for drawing position calculation with title protection.
 * These tests verify the logic used in EditorKmpInjector.addDrawingToDocument()
 * to ensure drawings are never placed at a position that would replace the title.
 */
class DrawingPositionCalculatorTest {

    /**
     * Calculates the position for a new drawing, protecting the title from being replaced.
     * This mirrors the logic in EditorKmpInjector.addDrawingToDocument().
     */
    private fun calculateDrawingPosition(content: Map<Double, StoryStep>?): Double {
        val endPosition = (content?.size ?: 0).toDouble()
        val storyAtEnd = content?.get(endPosition)
        return if (storyAtEnd?.type == StoryTypes.TITLE.type) {
            endPosition + 1
        } else {
            endPosition
        }
    }

    @Test
    fun drawingOnDocumentWithTitleAndContentGoesToEnd() {
        val content = mapOf(
            0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "My Title"),
            1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "Some content")
        )

        val position = calculateDrawingPosition(content)

        // Drawing should go to position 2.0 (after existing content)
        assertEquals(2.0, position, "Drawing should be placed at the end of content")
    }

    @Test
    fun drawingOnDocumentWithOnlyTitleGoesToPositionOne() {
        val content = mapOf(
            0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "My Title")
        )

        val position = calculateDrawingPosition(content)

        // Drawing should go to position 1.0 (after the title)
        assertEquals(1.0, position, "Drawing should be placed after the title")
    }

    @Test
    fun drawingOnEmptyDocumentGoesToPositionZero() {
        val content = emptyMap<Double, StoryStep>()

        val position = calculateDrawingPosition(content)

        // Drawing should go to position 0.0 (no content to protect)
        assertEquals(0.0, position, "Drawing should be placed at position 0 for empty document")
    }

    @Test
    fun drawingOnNullDocumentGoesToPositionZero() {
        val position = calculateDrawingPosition(null)

        // Drawing should go to position 0.0
        assertEquals(0.0, position, "Drawing should be placed at position 0 for null content")
    }

    @Test
    fun drawingOnDocumentWithMultipleItemsGoesToEnd() {
        val content = mapOf(
            0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "My Title"),
            1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "Content 1"),
            2.0 to StoryStep(type = StoryTypes.TEXT.type, text = "Content 2"),
            3.0 to StoryStep(type = StoryTypes.IMAGE.type, path = "/image.png")
        )

        val position = calculateDrawingPosition(content)

        // Drawing should go to position 4.0 (after all existing content)
        assertEquals(4.0, position, "Drawing should be placed at the end of all content")
    }

    @Test
    fun titleIsNeverReplacedByDrawing() {
        // This test ensures that when content exists, drawings never go to position 0
        // which would replace the title
        val content = mapOf(
            0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "Protected Title")
        )

        val position = calculateDrawingPosition(content)

        // Position should NOT be 0.0 (where the title is)
        assert(position > 0.0) { "Drawing should never be placed at position 0 when title exists" }
    }
}
