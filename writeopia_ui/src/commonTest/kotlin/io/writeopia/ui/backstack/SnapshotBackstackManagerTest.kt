package io.writeopia.ui.backstack

import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.utils.MapStoryData
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotBackstackManagerTest {

    private val backstackManager = SnapshotBackstackManager()
    private val state = StoryState(
        stories = MapStoryData.simpleMessages()
    )

    @Test
    fun `when adding the first action - the manager should notify it is possible to revert`() {
        backstackManager.addTextState(state, 2)

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(false, backstackManager.canRedo.value)
    }

    @Test
    fun `when adding and popping the stack - the manager should notify correctly`() {
        repeat(3) { i ->
            val state = StoryState(stories = mapOf(i to StoryStep(type = StoryTypes.TEXT.type)))

            backstackManager.addState(state)
        }

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(false, backstackManager.canRedo.value)

        backstackManager.previousState()

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(true, backstackManager.canRedo.value)

        backstackManager.previousState()

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(true, backstackManager.canRedo.value)

        backstackManager.previousState()

        assertEquals(false, backstackManager.canUndo.value)
        assertEquals(true, backstackManager.canRedo.value)
    }

    @Test
    fun `when adding a new story - it should no longer be possible to mover forward`() {
        repeat(3) { i ->
            val state = StoryState(stories = mapOf(i to StoryStep(type = StoryTypes.TEXT.type)))

            backstackManager.addState(state)
        }

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(false, backstackManager.canRedo.value)

        backstackManager.previousState()

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(true, backstackManager.canRedo.value)

        backstackManager.addState(
            StoryState(
                stories = mapOf(0 to StoryStep(type = StoryTypes.TEXT.type))
            )
        )

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(false, backstackManager.canRedo.value)
    }

    @Test
    fun `it should be possible to revert and redo a check`() {
        val state = StoryState(stories = mapOf(0 to StoryStep(type = StoryTypes.TEXT.type)))
        backstackManager.addState(state)

        val checkState =
            StoryState(stories = mapOf(0 to StoryStep(type = StoryTypes.CHECK_ITEM.type)))
        backstackManager.addState(checkState)

        val listItemState =
            StoryState(stories = mapOf(0 to StoryStep(type = StoryTypes.UNORDERED_LIST_ITEM.type)))
        backstackManager.addState(listItemState)

        assertEquals(listItemState, backstackManager.previousState())
        assertEquals(checkState, backstackManager.previousState())
        assertEquals(state, backstackManager.previousState())
    }
}
