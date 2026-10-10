package io.writeopia.writingagent.actions

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** Puts the answers of the AI into the document, like the editor's own AI commands do. */
object AiTextWriter {

    const val NO_AI_MESSAGE = "No AI is available. Pick one in the settings, or sign in to use the cloud AI."

    /** Streams [answers] into a new block inserted at [position]. */
    suspend fun streamAt(
        manager: WriteopiaStateManager,
        position: Double,
        answers: Flow<ResultData<String>>,
    ) {
        answers.onStart {
            manager.loadingAtPosition(position)
        }.onCompletion {
            manager.trackState()
        }.map { result ->
            when (result) {
                is ResultData.Complete -> result.data
                is ResultData.Error -> "Error. Message: ${result.exception?.message}"
                is ResultData.Loading,
                is ResultData.Idle,
                is ResultData.InProgress -> ""
            }
        }.collect { text ->
            manager.changeStoryState(
                Action.StoryStateChange(
                    storyStep = StoryStep(type = StoryTypes.AI_ANSWER.type, text = text),
                    position = position,
                ),
                trackIt = false
            )
        }
    }

    /** Says in the document, at [position], that no AI could answer. */
    fun noAiAt(manager: WriteopiaStateManager, position: Double) {
        manager.addAtPosition(
            StoryStep(type = StoryTypes.AI_ANSWER.type, text = NO_AI_MESSAGE),
            position
        )
    }

    /** The whole answer, once the stream ends. Each emission carries the answer so far. */
    suspend fun Flow<ResultData<String>>.finalText(): String? =
        filterIsInstance<ResultData.Complete<String>>()
            .map { it.data }
            .lastOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
