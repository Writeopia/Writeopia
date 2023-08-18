package com.github.leandroborgesferreira.storyteller.backstack

import com.github.leandroborgesferreira.storyteller.manager.ContentHandler
import com.github.leandroborgesferreira.storyteller.manager.MovementHandler
import com.github.leandroborgesferreira.storyteller.model.action.BackstackAction
import com.github.leandroborgesferreira.storyteller.model.story.LastEdit
import com.github.leandroborgesferreira.storyteller.model.story.StoryState
import com.github.leandroborgesferreira.storyteller.models.story.StoryStep
import com.github.leandroborgesferreira.storyteller.model.story.StoryTypes
import com.github.leandroborgesferreira.storyteller.utils.alias.UnitsNormalizationMap
import org.junit.Assert.*
import org.junit.Test

class PerStateBackstackManagerTest {

    private val stepsNormalizer: UnitsNormalizationMap = { storyMap ->
        storyMap.mapValues { (_, storyList) -> storyList[0] }
    }

    private val backstackManager =
        PerStateBackstackManager(
            contentHandler = ContentHandler(
                stepsNormalizer = stepsNormalizer
            ),
            movementHandler = MovementHandler(stepsNormalizer)
        )

    @Test
    fun `when adding the first action, the manager should notify it is possible to revert`() {
        backstackManager.addAction(
            BackstackAction.StoryStateChange(
                StoryStep(type = StoryTypes.MESSAGE.type),
                position = 0
            )
        )

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(false, backstackManager.canRedo.value)
    }

    @Test
    fun `when adding and popping the stack, the manager should notify correctly`() {
        val state = buildMap {
            repeat(3) { i ->
                val storyStep =
                    StoryStep(type = StoryTypes.MESSAGE.type)
                val action = BackstackAction.StoryStateChange(storyStep, position = i)

                backstackManager.addAction(action)
                this[i] = storyStep
            }
        }

        val mockState = StoryState(state, LastEdit.Nothing)

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(false, backstackManager.canRedo.value)

        backstackManager.previousState(mockState)

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(true, backstackManager.canRedo.value)

        backstackManager.previousState(mockState)

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(true, backstackManager.canRedo.value)

        backstackManager.previousState(mockState)

        assertEquals(false, backstackManager.canUndo.value)
        assertEquals(true, backstackManager.canRedo.value)
    }

    @Test
    fun `when adding a new story, it should no longer be possible to mover forward`() {
        val state = buildMap {
            repeat(3) { i ->
                val storyStep =
                    StoryStep(type = StoryTypes.MESSAGE.type)
                val action = BackstackAction.StoryStateChange(storyStep, position = i)

                backstackManager.addAction(action)
                this[i] = storyStep
            }
        }

        val mockState = StoryState(state, LastEdit.Nothing)

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(false, backstackManager.canRedo.value)

        backstackManager.previousState(mockState)

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(true, backstackManager.canRedo.value)

        backstackManager.addAction(
            BackstackAction.StoryStateChange(
                StoryStep(type = StoryTypes.MESSAGE.type),
                position = 3
            )
        )

        assertEquals(true, backstackManager.canUndo.value)
        assertEquals(false, backstackManager.canRedo.value)
    }

    @Test
    fun `when adding many text some of the state should be merged`() {
        val mockState = StoryState(emptyMap(), LastEdit.Nothing)
        val stringBuilder = StringBuilder()

        repeat(40) { i ->
            stringBuilder.append(i)

            backstackManager.addAction(
                BackstackAction.StoryTextChange(
                    StoryStep(
                        type = StoryTypes.MESSAGE.type,
                        text = stringBuilder.toString()
                    ),
                    position = 0
                )
            )
        }

        backstackManager.previousState(mockState)
        backstackManager.previousState(mockState)

        assertEquals(false, backstackManager.canUndo.value)
    }

    @Test
    fun `it should be possible to revert and redo a check`() {
        val story1 = StoryStep(
            type = StoryTypes.CHECK_ITEM.type,
            checked = false
        )
        val story2 = story1.copy(checked = true)
        val mockState1 = StoryState(mapOf(0 to story1), LastEdit.Nothing)
        val mockState2 = StoryState(mapOf(0 to story2), LastEdit.Nothing)

        backstackManager.addAction(BackstackAction.StoryStateChange(story1, 0))

        val previousState = backstackManager.previousState(mockState2)
        assertEquals(false, previousState.stories[0]!!.checked)

        val newState = backstackManager.nextState(previousState)
        assertEquals(true, newState.stories[0]!!.checked)
    }
}
