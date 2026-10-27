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
import io.writeopia.sdk.utils.iterables.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val r0 = Rank.at(0)
private val r1 = Rank.at(1)
private val r2 = Rank.at(2)
private val r3 = Rank.at(3)
private val r4 = Rank.at(4)
private val r1b = "${r1}i"

private fun text(text: String) = StoryStep(type = StoryTypes.TEXT.type, text = text)

/** title, A, B, C at ranks 0, 1, between 1 and 2, and 2. */
private fun document(): Map<String, StoryStep> = NextPositionCalculator.calculate(
    mapOf(r0 to text("title"), r1 to text("A"), r1b to text("B"), r2 to text("C"))
)

private fun Map<String, StoryStep>.linksFollowTheKeys() {
    val keys = keys.toList()
    keys.forEachIndexed { index, key ->
        assertEquals(keys.getOrNull(index - 1), getValue(key).previousPosition, "previous of $key")
        assertEquals(keys.getOrNull(index + 1), getValue(key).nextPosition, "next of $key")
    }
}

/**
 * The positions of the steps are ranks, not indexes: inserting, deleting and moving steps never
 * changes the positions of the other steps, and the links between neighbours follow the map,
 * never a stale copy.
 */
class PositionKeepingTest {

    private val handler = ContentHandler(
        stepsNormalizer = StepsMapNormalizationBuilder.reduceNormalizations { defaultNormalizers() }
    )

    @Test
    fun `an inserted step keeps the positions and links of the others`() {
        val result = handler.addNewContent(document(), text("X"), r3).map

        assertEquals(listOf("title", "A", "B", "C", "X"), result.values.map { it.text })
        assertEquals(listOf(r0, r1, r1b, r2, r3), result.keys.toList())
        result.linksFollowTheKeys()
    }

    @Test
    fun `a line break after an insertion goes between the line and the next one`() {
        val afterInsert = handler.addNewContent(document(), text("X"), r3).map
        val b = afterInsert.getValue(r1b)

        val (_, state) = handler.onLineBreak(afterInsert, Action.LineBreak(b.copy(text = "B\n"), r1b))

        assertEquals(listOf("title", "A", "B", "", "C", "X"), state.stories.values.map { it.text })
        val newKey = (state.lastEdit as LastEdit.LineBreakEdition).newStep.first
        assertTrue(newKey > r1b && newKey < r2)
        assertEquals("B", state.stories[r1b]?.text)
        state.stories.linksFollowTheKeys()
    }

    @Test
    fun `a line break uses the neighbour from the map, not a stale link`() {
        // The links say B is followed by C, but the map has a step in between already
        val between = Rank.between(r1b, r2)
        val stories = document().toMutableMap().apply { this[between] = text("between") }
        val b = stories.getValue(r1b)

        val (_, state) = handler.onLineBreak(stories, Action.LineBreak(b.copy(text = "B\n"), r1b))

        assertEquals(listOf("title", "A", "B", "", "between", "C"), state.stories.values.map { it.text })
    }

    @Test
    fun `a line break between adjacent integers never moves the next lines`() {
        val packed = NextPositionCalculator.calculate(
            mapOf(r0 to text("title"), r1 to text("A"), r2 to text("B"), r3 to text("C"))
        )

        val (_, state) = handler.onLineBreak(packed, Action.LineBreak(packed.getValue(r1).copy(text = "A\n"), r1))

        assertEquals(listOf("title", "A", "", "B", "C"), state.stories.values.map { it.text })
        assertEquals("B", state.stories[r2]?.text)
        assertEquals("C", state.stories[r3]?.text)
        assertTrue(state.lastEdit is LastEdit.LineBreakEdition)
        state.stories.linksFollowTheKeys()
    }

    @Test
    fun `pasted lines go after the line, in order`() {
        val a = document().getValue(r1)

        val (_, state) = handler.onLineBreak(document(), Action.LineBreak(a.copy(text = "A\nx\ny"), r1))

        assertEquals(listOf("title", "A", "x", "y", "B", "C"), state.stories.values.map { it.text })
        assertEquals(r1b, state.stories.keys.toList()[4])
        state.stories.linksFollowTheKeys()
    }

    @Test
    fun `a removed step leaves the positions of the others and relinks the neighbours`() {
        val result = handler.removeContent(document(), r1b)

        assertEquals(listOf(r0, r1, r2), result.keys.toList())
        result.linksFollowTheKeys()
    }

    @Test
    fun `a deleted step relinks the neighbours from the map`() {
        val stories = document()
        val state = handler.deleteStory(
            Action.DeleteStory(stories.getValue(r1b), r1b),
            stories,
            documentId = "doc"
        )

        val result = state?.stories ?: error("no state")
        assertEquals(listOf(r0, r1, r2), result.keys.toList())
        result.linksFollowTheKeys()
    }

    @Test
    fun `a moved step lands between the target and its next, nothing else moves`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(r2), positionFrom = r2, positionTo = r1)
        )

        assertEquals(listOf("title", "A", "C", "B"), result.values.map { it.text })
        val keys = result.keys.toList()
        assertEquals(r0, keys[0])
        assertEquals(r1, keys[1])
        assertTrue(keys[2] > r1 && keys[2] < r1b)
        assertEquals(r1b, keys[3])
        result.linksFollowTheKeys()
    }

    @Test
    fun `a move onto the step right before it changes nothing`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(r1b), positionFrom = r1b, positionTo = r1)
        )

        assertEquals(stories, result)
    }

    @Test
    fun `a move uses the neighbours from the map, not stale links`() {
        // The links are from before the step in between was added
        val stories = document().toMutableMap().apply { this[Rank.between(r1b, r2)] = text("between") }

        val result = MovementHandler().move(
            stories,
            Action.Move(stories.getValue(r2), positionFrom = r2, positionTo = r1b)
        )

        assertEquals(listOf("title", "A", "B", "C", "between"), result.values.map { it.text })
        assertTrue(result.keys.toList() == result.keys.sorted())
    }

    @Test
    fun `a bulk move keeps the order of the moved steps`() {
        val stories = document()

        val result = MovementHandler().move(
            stories,
            Action.BulkMove(emptyList(), positionFrom = setOf(r1b, r2), positionTo = r0)
        )

        assertEquals(listOf("title", "B", "C", "A"), result.values.map { it.text })
        result.linksFollowTheKeys()
    }

    @Test
    fun `the calculator sorts and links whatever order the map comes in`() {
        val result = NextPositionCalculator.calculate(
            linkedMapOf(r2 to text("C"), r0 to text("title"), r1 to text("A"))
        )

        assertEquals(listOf(r0, r1, r2), result.keys.toList())
        assertEquals(r1, result[r0]?.nextPosition)
        assertEquals(r1, result[r2]?.previousPosition)
    }
}

class FocusHandlerWalkTest {

    private val handler = FocusHandler()

    private fun image() = StoryStep(type = StoryTypes.IMAGE.type)

    private val stories = mapOf(r0 to text("title"), r1 to text("a"), r1b to image(), r2 to image(), r3 to text("b"))

    @Test
    fun `the next focus skips the steps that can't take it`() {
        assertEquals(r3, handler.findNextFocus(r1, stories))
        assertEquals(r3, handler.findNextFocus(r1b, stories))
        assertNull(handler.findNextFocus(r3, stories))
    }

    @Test
    fun `the previous focus skips the steps that can't take it`() {
        assertEquals(r1, handler.findPreviousFocus(r3, stories))
        assertEquals(r1, handler.findPreviousFocus(r1b, stories))
        assertNull(handler.findPreviousFocus(r0, stories))
    }
}

class BulkRelinkTest {

    private val handler = ContentHandler(
        stepsNormalizer = StepsMapNormalizationBuilder.reduceNormalizations { defaultNormalizers() }
    )

    private val stories = NextPositionCalculator.calculate(
        mapOf(r0 to text("title"), r1 to text("a"), r2 to text("b"), r3 to text("c"), r4 to text("d"))
    )

    @Test
    fun `a bulk deletion relinks the steps around the gap`() {
        val (remaining, deleted) = handler.bulkDeletion(listOf(r2, r3), stories)

        assertEquals(setOf(r2, r3), deleted.keys)
        remaining.linksFollowTheKeys()
    }

    @Test
    fun `bulk added steps are linked in order with their neighbours`() {
        val result = handler.addNewContents(stories, listOf(text("x"), text("y")), r2).map

        assertEquals(listOf("title", "a", "x", "y", "b", "c", "d"), result.values.map { it.text })
        result.linksFollowTheKeys()
    }

    @Test
    fun `steps added after a step with adjacent integers never move the others`() {
        val insertion = handler.addNewContentsAfter(stories, listOf(text("x"), text("y")), r1)

        assertEquals(listOf("title", "a", "x", "y", "b", "c", "d"), insertion.map.values.map { it.text })
        assertEquals("b", insertion.map[r2]?.text)
        insertion.map.linksFollowTheKeys()
    }
}
