package io.writeopia.manager

import io.writeopia.sdk.manager.ContentHandler
import io.writeopia.sdk.manager.FocusHandler
import io.writeopia.sdk.manager.MovementHandler
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.utils.NextPositionCalculator
import io.writeopia.sdk.utils.iterables.POSITION_GAP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val gap = POSITION_GAP
private const val half = gap / 2

private fun text(text: String) = StoryStep(type = StoryTypes.TEXT.type, text = text)

/** title, A, B, C at 0, 1, 1.5 and 2 gaps. */
private fun document(): Map<Long, StoryStep> = NextPositionCalculator.calculate(
    mapOf(0L to text("title"), gap to text("A"), gap + half to text("B"), 2 * gap to text("C"))
)

private fun Map<Long, StoryStep>.linksFollowTheKeys() {
    val keys = keys.toList()
    keys.forEachIndexed { index, key ->
        assertEquals(keys.getOrNull(index - 1), getValue(key).previousPosition, "previous of $key")
        assertEquals(keys.getOrNull(index + 1), getValue(key).nextPosition, "next of $key")
    }
}

/**
 * The positions of the steps are sort keys, not indexes: inserting, deleting and moving steps
 * never changes the positions of the other steps (unless there is no room left between two
 * of them), and the links between neighbours follow the map, never a stale copy.
 */
class PositionKeepingTest {

    private val handler = ContentHandler(
        stepsNormalizer = StepsMapNormalizationBuilder.reduceNormalizations { defaultNormalizers() }
    )

    @Test
    fun `an inserted step keeps the positions and links of the others`() {
        val result = handler.addNewContent(document(), text("X"), 3 * gap).map

        assertEquals(listOf("title", "A", "B", "C", "X"), result.values.map { it.text })
        assertEquals(listOf(0L, gap, gap + half, 2 * gap, 3 * gap), result.keys.toList())
        result.linksFollowTheKeys()
    }

    @Test
    fun `a line break after an insertion goes between the line and the next one`() {
        val afterInsert = handler.addNewContent(document(), text("X"), 3 * gap).map
        val b = afterInsert.getValue(gap + half)

        val (_, state) = handler.onLineBreak(afterInsert, Action.LineBreak(b.copy(text = "B\n"), gap + half))

        assertEquals(listOf("title", "A", "B", "", "C", "X"), state.stories.values.map { it.text })
        assertEquals(gap + half + half / 2, (state.lastEdit as LastEdit.LineBreakEdition).newStep.first)
        assertEquals("B", state.stories[gap + half]?.text)
        state.stories.linksFollowTheKeys()
    }

    @Test
    fun `a line break uses the neighbour from the map, not a stale link`() {
        // The links say B is followed by 2 gaps, but the map has a step in between already
        val stories = document().toMutableMap().apply { this[gap + half + half / 2] = text("between") }
        val b = stories.getValue(gap + half)

        val (_, state) = handler.onLineBreak(stories, Action.LineBreak(b.copy(text = "B\n"), gap + half))

        assertEquals(listOf("title", "A", "B", "", "between", "C"), state.stories.values.map { it.text })
    }

    @Test
    fun `a line break with no room spreads the next lines out and saves them too`() {
        val packed = NextPositionCalculator.calculate(
            mapOf(0L to text("title"), 1L to text("A"), 2L to text("B"), 3L to text("C"))
        )

        val (_, state) = handler.onLineBreak(packed, Action.LineBreak(packed.getValue(1L).copy(text = "A\n"), 1L))

        assertEquals(listOf("title", "A", "", "B", "C"), state.stories.values.map { it.text })
        val saved = (state.lastEdit as LastEdit.BulkEdition).steps.map { (_, step) -> step.text }
        assertEquals(setOf("A", "", "B", "C"), saved.toSet())
        state.stories.linksFollowTheKeys()
    }

    @Test
    fun `pasted lines go after the line, in order`() {
        val a = document().getValue(gap)

        val (_, state) = handler.onLineBreak(document(), Action.LineBreak(a.copy(text = "A\nx\ny"), gap))

        assertEquals(listOf("title", "A", "x", "y", "B", "C"), state.stories.values.map { it.text })
        assertEquals(gap + half, state.stories.keys.toList()[4])
        state.stories.linksFollowTheKeys()
    }

    @Test
    fun `a removed step leaves the positions of the others and relinks the neighbours`() {
        val result = handler.removeContent(document(), gap + half)

        assertEquals(listOf(0L, gap, 2 * gap), result.keys.toList())
        result.linksFollowTheKeys()
    }

    @Test
    fun `a deleted step relinks the neighbours from the map`() {
        val stories = document()
        val state = handler.deleteStory(
            Action.DeleteStory(stories.getValue(gap + half), gap + half),
            stories,
            documentId = "doc"
        )

        val result = state?.stories ?: error("no state")
        assertEquals(listOf(0L, gap, 2 * gap), result.keys.toList())
        result.linksFollowTheKeys()
    }

    @Test
    fun `a moved step lands between the target and its next, nothing else moves`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(2 * gap), positionFrom = 2 * gap, positionTo = gap)
        )

        assertEquals(listOf("title", "A", "C", "B"), result.values.map { it.text })
        assertEquals(listOf(0L, gap, gap + half / 2, gap + half), result.keys.toList())
        result.linksFollowTheKeys()
    }

    @Test
    fun `a move onto the step right before it changes nothing`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(gap + half), positionFrom = gap + half, positionTo = gap)
        )

        assertEquals(stories, result)
    }

    @Test
    fun `a move uses the neighbours from the map, not stale links`() {
        // The links are from before the step in between was added
        val stories = document().toMutableMap().apply { this[gap + half + half / 2] = text("between") }

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(2 * gap), positionFrom = 2 * gap, positionTo = gap + half)
        )

        assertEquals(listOf("title", "A", "B", "C", "between"), result.values.map { it.text })
        assertTrue(result.keys.toList() == result.keys.sorted())
    }

    @Test
    fun `a bulk move keeps the order of the moved steps`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.BulkMove(emptyList(), positionFrom = setOf(gap + half, 2 * gap), positionTo = 0L)
        )

        assertEquals(listOf("title", "B", "C", "A"), result.values.map { it.text })
        result.linksFollowTheKeys()
    }

    @Test
    fun `the calculator sorts and links whatever order the map comes in`() {
        val result = NextPositionCalculator.calculate(
            linkedMapOf(2L to text("C"), 0L to text("title"), 1L to text("A"))
        )

        assertEquals(listOf(0L, 1L, 2L), result.keys.toList())
        assertEquals(1L, result[0L]?.nextPosition)
        assertEquals(1L, result[2L]?.previousPosition)
    }
}

class FocusHandlerWalkTest {

    private val handler = FocusHandler()

    private fun image() = StoryStep(type = StoryTypes.IMAGE.type)

    private val stories = mapOf(0L to text("title"), gap to text("a"), gap + half to image(), 2 * gap to image(), 3 * gap to text("b"))

    @Test
    fun `the next focus skips the steps that can't take it`() {
        assertEquals(3 * gap, handler.findNextFocus(gap, stories))
        assertEquals(3 * gap, handler.findNextFocus(gap + half, stories))
        assertNull(handler.findNextFocus(3 * gap, stories))
    }

    @Test
    fun `the previous focus skips the steps that can't take it`() {
        assertEquals(gap, handler.findPreviousFocus(3 * gap, stories))
        assertEquals(gap, handler.findPreviousFocus(gap + half, stories))
        assertNull(handler.findPreviousFocus(0L, stories))
    }
}

class BulkRelinkTest {

    private val handler = ContentHandler(
        stepsNormalizer = StepsMapNormalizationBuilder.reduceNormalizations { defaultNormalizers() }
    )

    private val stories = NextPositionCalculator.calculate(
        mapOf(0L to text("title"), gap to text("a"), 2 * gap to text("b"), 3 * gap to text("c"), 4 * gap to text("d"))
    )

    @Test
    fun `a bulk deletion relinks the steps around the gap`() {
        val (remaining, deleted) = handler.bulkDeletion(listOf(2 * gap, 3 * gap), stories)

        assertEquals(setOf(2 * gap, 3 * gap), deleted.keys)
        remaining.linksFollowTheKeys()
    }

    @Test
    fun `bulk added steps are linked in order with their neighbours`() {
        val result = handler.addNewContents(stories, listOf(text("x"), text("y")), 2 * gap).map

        assertEquals(listOf("title", "a", "x", "y", "b", "c", "d"), result.values.map { it.text })
        result.linksFollowTheKeys()
    }

    @Test
    fun `steps added after a step with no room spread the others out and report them`() {
        val packed = NextPositionCalculator.calculate(mapOf(0L to text("title"), 1L to text("a"), 2L to text("b")))

        val insertion = handler.addNewContentsAfter(packed, listOf(text("x"), text("y")), 1L)

        assertEquals(listOf("title", "a", "x", "y", "b"), insertion.map.values.map { it.text })
        assertEquals(listOf("b"), insertion.moved.map { key -> insertion.map.getValue(key).text })
        insertion.map.linksFollowTheKeys()
    }
}
