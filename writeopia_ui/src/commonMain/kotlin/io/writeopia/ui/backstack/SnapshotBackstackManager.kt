package io.writeopia.ui.backstack

import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.story.StoryStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_TEXT_EDIT_LIMIT = 7

class SnapshotBackstackManager(
    private val textEditLimit: Int = DEFAULT_TEXT_EDIT_LIMIT,
) : BackstackInform {

    private var lastEditPosition: Int = -1
    private var textEditCount = 0

    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)

    override val canUndo: StateFlow<Boolean> = _canUndo
    override val canRedo: StateFlow<Boolean> = _canRedo

    private val backStateList: MutableList<StoryState> = mutableListOf()
    private val forwardStateList: MutableList<StoryState> = mutableListOf()

    private val storyState: MutableMap<Int, StoryStep> = mutableMapOf()

    fun previousState(state: StoryState): StoryState? = if (backStateList.isEmpty()) {
        null
    } else {
        forwardStateList.add(state)
        backStateList.removeLast().also {
            updateInformInfo()
        }
    }

    fun nextState(): StoryState? = if (forwardStateList.isEmpty()) {
        null
    } else {
        forwardStateList.removeLast().also { state ->
            backStateList.add(state)
            updateInformInfo()
        }
    }

    fun peek(): StoryState? = if (backStateList.isNotEmpty()) backStateList.last() else null

    /**
     * Adds a state by checking the difference between current state and previous one.
     *
     * When adding state it is crucial that old stories that didn't change are not copied to avoid
     * memory waste.
     */
    fun addState(state: StoryState) {
        var isDifferent = false

        // It uses only the new stories and unchanged old ones.
        val newStories = state.stories.mapValues { (_, story) ->
            val code = story.persistentHashcode()

            if (!storyState.containsKey(code)) {
                isDifferent = true
                storyState[code] = story
            }

            storyState[code]!!
        }

        if (isDifferent) {
            backStateList.add(state.copy(stories = newStories))
            forwardStateList.clear()

            updateInformInfo()
        }
    }

    fun addTextState(state: StoryState, position: Int) {
        if (position == lastEditPosition) {
            if (textEditCount < textEditLimit) {
                textEditCount++
            } else {
                lastEditPosition = position
                textEditCount = 1

                addState(state)
            }
        } else {
            lastEditPosition = position
            textEditCount = 1

            addState(state)
        }
    }

    private fun updateInformInfo() {
        _canUndo.value = backStateList.isNotEmpty()
        _canRedo.value = forwardStateList.isNotEmpty()
    }

    /**
     * This is a hash code that contains information that don't change very often, like localId.
     */
    private fun StoryStep.persistentHashcode(): Int {
        return listOf(
            id,
            type,
            parentId,
            url,
            path,
            text,
            checked,
            steps,
            tags,
            spans,
            decoration,
            ephemeral,
            documentLink
        ).hashCode()
    }
}
