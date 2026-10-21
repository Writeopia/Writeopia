package io.writeopia.writingagent.actions

import io.writeopia.ai.AiCommand
import io.writeopia.ai.AiStreaming
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.CancellationException
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
        // One step for the whole answer, rewritten at each chunk and ephemeral while the answer
        // comes: nothing is saved nor synced until the stream ends, then the final text once.
        val answerId = GenerateId.generate()
        var text = ""
        // Where the loading story went: at [position], or right before the story there.
        var answerPosition = position

        fun step(ephemeral: Boolean) =
            StoryStep(id = answerId, type = type, text = prefix + text.trim(), tags = tags, ephemeral = ephemeral)

        answers.onStart {
            answerPosition = manager.loadingAtPosition(position)
        }.onCompletion { cause ->
            // Leaving the document cancels the stream: the answer is dropped, never saved.
            if (cause is CancellationException) return@onCompletion
            manager.changeStoryState(Action.StoryStateChange(step(ephemeral = false), answerPosition), trackIt = false)
            manager.trackState()
        }.map { result ->
            when (result) {
                is ResultData.Complete -> result.data
                is ResultData.Error -> "Error. Message: ${result.exception?.message}"
                is ResultData.Loading,
                is ResultData.Idle,
                is ResultData.InProgress -> ""
            }
        }.collect { chunk ->
            text = chunk
            manager.changeStoryState(Action.StoryStateChange(step(ephemeral = true), answerPosition), trackIt = false)
        }
    }

    /** Says in the document, at [position], that no AI could answer. */
    fun noAiAt(manager: WriteopiaStateManager, position: Double) {
        manager.addAtPosition(
            StoryStep(type = StoryTypes.AI_ANSWER.type, text = NO_AI_MESSAGE),
            position
        )
    }

    /** One line of text from the AI, cleaned of the Markdown and quotes it tends to add. */
    suspend fun AiStreaming.askOneLine(instructions: String, text: String): Result<String> =
        runCatching {
            stream(AiCommand.PROMPT, "$instructions\n```\n$text\n```")
                .finalTextOrError()
                .getOrThrow()
                .lines()
                .first { it.isNotBlank() }
                .trim('#', ' ', '"', '*', '.')
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
