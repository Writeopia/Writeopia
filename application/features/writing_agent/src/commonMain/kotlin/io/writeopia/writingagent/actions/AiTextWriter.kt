package io.writeopia.writingagent.actions

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** Puts the answers of the AI into the document, like the editor's own AI commands do. */
object AiTextWriter {

    const val NO_AI_MESSAGE = "No AI is available. Pick one in the settings, or sign in to use the cloud AI."

    /**
     * Streams [answers] into a new block inserted at [position]. The block is an AI answer unless
     * another [type] is asked for; [prefix] goes before the text and [tags] decorate the block.
     */
    suspend fun streamAt(
        manager: WriteopiaStateManager,
        position: Double,
        answers: Flow<ResultData<String>>,
        type: StoryType = StoryTypes.AI_ANSWER.type,
        tags: Set<TagInfo> = emptySet(),
        prefix: String = "",
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
                    storyStep = StoryStep(type = type, text = prefix + text.trim(), tags = tags),
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

    /**
     * The whole answer, once the stream ends. Each emission carries the answer so far. An error
     * in the stream, or an empty answer, is a failure with the reason.
     */
    suspend fun Flow<ResultData<String>>.finalTextOrError(): Result<String> {
        var text: String? = null
        var error: Exception? = null

        collect { result ->
            when (result) {
                is ResultData.Complete -> text = result.data
                is ResultData.Error -> error = result.exception ?: Exception("The AI failed to answer")
                is ResultData.Loading,
                is ResultData.Idle,
                is ResultData.InProgress -> {}
            }
        }

        val answer = text?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            error != null -> Result.failure(error)
            answer == null -> Result.failure(Exception("The AI answered nothing"))
            else -> Result.success(answer)
        }
    }
}
