package io.writeopia.manager

import io.writeopia.sdk.manager.ContentHandler
import io.writeopia.sdk.manager.MovementHandler
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.utils.NextPositionCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The positions of the steps are sort keys, not indexes: inserting, deleting and moving steps
 * never changes the positions of the other steps, and the links between neighbours follow the
 * map, never a stale copy.
 */
class PositionKeepingTest {

    private val handler = ContentHandler(
        stepsNormalizer = StepsMapNormalizationBuilder.reduceNormalizations { defaultNormalizers() }
    )

    private fun text(text: String) = StoryStep(type = StoryTypes.TEXT.type, text = text)

    private fun document(): Map<Double, StoryStep> = NextPositionCalculator.calculate(
        mapOf(0.0 to text("title"), 1.0 to text("A"), 1.5 to text("B"), 2.0 to text("C"))
    )

    @Test
    fun `an inserted step keeps the positions and links of the others`() {
        val result = handler.addNewContent(document(), text("X"), 3.0)

        assertEquals(listOf("title", "A", "B", "C", "X"), result.values.map { it.text })
        assertEquals(listOf(0.0, 1.0, 1.5, 2.0, 3.0), result.keys.toList())
        assertEquals(3.0, result[2.0]?.nextPosition)
        assertEquals(2.0, result[3.0]?.previousPosition)
        assertNull(result[3.0]?.nextPosition)
    }

    @Test
    fun `a line break after an insertion goes between the line and the next one`() {
        val afterInsert = handler.addNewContent(document(), text("X"), 3.0)
        val b = afterInsert.getValue(1.5)

        val (_, state) = handler.onLineBreak(afterInsert, Action.LineBreak(b.copy(text = "B\n"), 1.5))

        assertEquals(listOf("title", "A", "B", "", "C", "X"), state.stories.values.map { it.text })
        assertEquals(1.75, (state.lastEdit as LastEdit.LineBreakEdition).newStep.first)
        assertEquals("B", state.stories[1.5]?.text)
    }

    @Test
    fun `a line break uses the neighbour from the map, not a stale link`() {
        // The links say B is followed by 2.0, but the map says there is a step at 1.75 already
        val stories = document().toMutableMap().apply { this[1.75] = text("between") }
        val b = stories.getValue(1.5)

        val (_, state) = handler.onLineBreak(stories, Action.LineBreak(b.copy(text = "B\n"), 1.5))

        assertEquals(listOf("title", "A", "B", "", "between", "C"), state.stories.values.map { it.text })
    }

    @Test
    fun `a removed step leaves the positions of the others and relinks the neighbours`() {
        val result = handler.removeContent(document(), 1.5)

        assertEquals(listOf(0.0, 1.0, 2.0), result.keys.toList())
        assertEquals(2.0, result[1.0]?.nextPosition)
        assertEquals(1.0, result[2.0]?.previousPosition)
    }

    @Test
    fun `a deleted step relinks the neighbours from the map`() {
        val stories = document()
        val state = handler.deleteStory(
            Action.DeleteStory(stories.getValue(1.5), 1.5),
            stories,
            documentId = "doc"
        )

        val result = state?.stories ?: error("no state")
        assertEquals(listOf(0.0, 1.0, 2.0), result.keys.toList())
        assertEquals(2.0, result[1.0]?.nextPosition)
        assertEquals(1.0, result[2.0]?.previousPosition)
    }

    @Test
    fun `a moved step lands between the target and its next, nothing else moves`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(2.0), positionFrom = 2.0, positionTo = 1.0)
        )

        assertEquals(listOf("title", "A", "C", "B"), result.values.map { it.text })
        assertEquals(listOf(0.0, 1.0, 1.25, 1.5), result.keys.toList())
        assertEquals(1.25, result[1.0]?.nextPosition)
        assertEquals(1.25, result[1.5]?.previousPosition)
        assertNull(result[1.5]?.nextPosition)
    }

    @Test
    fun `a move onto the step right before it changes nothing`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(1.5), positionFrom = 1.5, positionTo = 1.0)
        )

        assertEquals(stories, result)
    }

    @Test
    fun `a move uses the neighbours from the map, not stale links`() {
        // The links are from before 1.75 was added
        val stories = document().toMutableMap().apply { this[1.75] = text("between") }

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(2.0), positionFrom = 2.0, positionTo = 1.5)
        )

        assertEquals(listOf("title", "A", "B", "C", "between"), result.values.map { it.text })
        assertTrue(result.keys.toList() == result.keys.sorted())
    }

    @Test
    fun `a bulk move keeps the order of the moved steps`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.BulkMove(emptyList(), positionFrom = setOf(1.5, 2.0), positionTo = 0.0)
        )

        assertEquals(listOf("title", "B", "C", "A"), result.values.map { it.text })
        assertEquals(listOf(0.0), result.keys.filter { it < 0.5 })
    }

    @Test
    fun `the calculator sorts and links whatever order the map comes in`() {
        val result = NextPositionCalculator.calculate(
            linkedMapOf(2.0 to text("C"), 0.0 to text("title"), 1.0 to text("A"))
        )

        assertEquals(listOf(0.0, 1.0, 2.0), result.keys.toList())
        assertEquals(1.0, result[0.0]?.nextPosition)
        assertEquals(1.0, result[2.0]?.previousPosition)
    }
}

class FocusHandlerWalkTest {

    private val handler = io.writeopia.sdk.manager.FocusHandler()

    private fun text(text: String) = StoryStep(type = StoryTypes.TEXT.type, text = text)

    private fun image() = StoryStep(type = StoryTypes.IMAGE.type)

    private val stories = mapOf(0.0 to text("title"), 1.0 to text("a"), 1.5 to image(), 2.0 to image(), 3.0 to text("b"))

    @Test
    fun `the next focus skips the steps that can't take it`() {
        assertEquals(3.0, handler.findNextFocus(1.0, stories))
        assertEquals(3.0, handler.findNextFocus(1.5, stories))
        assertNull(handler.findNextFocus(3.0, stories))
    }

    @Test
    fun `the previous focus skips the steps that can't take it`() {
        assertEquals(1.0, handler.findPreviousFocus(3.0, stories))
        assertEquals(1.0, handler.findPreviousFocus(1.5, stories))
        assertNull(handler.findPreviousFocus(0.0, stories))
    }
}

class BulkRelinkTest {

    private val handler = ContentHandler(
        stepsNormalizer = StepsMapNormalizationBuilder.reduceNormalizations { defaultNormalizers() }
    )

    private fun text(text: String) = StoryStep(type = StoryTypes.TEXT.type, text = text)

    private val stories = NextPositionCalculator.calculate(
        mapOf(0.0 to text("title"), 1.0 to text("a"), 2.0 to text("b"), 3.0 to text("c"), 4.0 to text("d"))
    )

    @Test
    fun `a bulk deletion relinks the steps around the gap`() {
        val (remaining, deleted) = handler.bulkDeletion(listOf(2.0, 3.0), stories)

        assertEquals(setOf(2.0, 3.0), deleted.keys)
        assertEquals(4.0, remaining[1.0]?.nextPosition)
        assertEquals(1.0, remaining[4.0]?.previousPosition)
    }

    @Test
    fun `bulk added steps are linked in order with their neighbours`() {
        val result = handler.addNewContents(stories, listOf(text("x"), text("y")), 2.0)

        assertEquals(listOf("title", "a", "x", "y", "b", "c", "d"), result.values.map { it.text })
        val keys = result.keys.toList()
        keys.zipWithNext().forEach { (previous, next) ->
            assertEquals(next, result[previous]?.nextPosition)
            assertEquals(previous, result[next]?.previousPosition)
        }
    }
}
