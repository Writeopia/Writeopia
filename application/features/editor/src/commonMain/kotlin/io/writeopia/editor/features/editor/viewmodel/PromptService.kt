package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Streams the answers of the AI into the document. The AI itself is whatever the user picked, so
 * the callers only hand over the function that streams the answer of a prompt.
 */
object PromptService {

    const val NO_AI_MESSAGE = "No AI is available. Pick one in the settings, or sign in to use the cloud AI."

    /** Streams the answer about the text [targetMode] points to, right after it. */
    suspend fun documentPrompt(
        targetMode: AiTargetMode,
        writeopiaManager: WriteopiaStateManager,
        streamFn: (String) -> Flow<ResultData<String>>,
    ) {
        val (text, position) = getTextAndPosition(targetMode, writeopiaManager)

        if (text == null) return

        streamFn(text).handleStream(writeopiaManager, position)
    }

    /** Streams the answer of [prompt] at [promptPosition], or after the cursor. */
    suspend fun prompt(
        prompt: String?,
        writeopiaManager: WriteopiaStateManager,
        streamFn: (String) -> Flow<ResultData<String>>,
        promptPosition: Double? = null
    ) {
        val position = promptPosition ?: writeopiaManager.getNextPosition()

        if (prompt != null && position != null) {
            streamFn(prompt).handleStream(writeopiaManager, position)
        }
    }

    /** Tells the user, in the document, that no AI could answer where [targetMode] points to. */
    fun noAiAvailable(targetMode: AiTargetMode, writeopiaManager: WriteopiaStateManager) {
        val (_, position) = getTextAndPosition(targetMode, writeopiaManager)
        noAiAvailableAt(position, writeopiaManager)
    }

    fun noAiAvailableAt(position: Double, writeopiaManager: WriteopiaStateManager) {
        writeopiaManager.changeStoryState(
            Action.StoryStateChange(
                storyStep = StoryStep(type = StoryTypes.AI_ANSWER.type, text = NO_AI_MESSAGE),
                position = position,
            )
        )
    }

    private fun getTextAndPosition(
        targetMode: AiTargetMode,
        writeopiaManager: WriteopiaStateManager
    ): Pair<String?, Double> {
        val lastPos = writeopiaManager.lastPosition()
        return when (targetMode) {
            AiTargetMode.DOCUMENT -> {
                val docText = writeopiaManager.getDocumentText()
                val pos = writeopiaManager.getStory(lastPos)?.nextPosition ?: (lastPos + 1)
                docText to pos
            }
            AiTargetMode.SELECTED_LINES -> {
                val selText = writeopiaManager.getCurrentSelectionText()
                val pos = writeopiaManager.positionAfterSelection()
                    ?: writeopiaManager.getNextPosition()
                    ?: writeopiaManager.getStory(lastPos)?.nextPosition
                    ?: (lastPos + 1)
                selText to pos
            }
            AiTargetMode.CURSOR -> {
                val cursorText = writeopiaManager.getCurrentText()
                val pos = writeopiaManager.getNextPosition()
                    ?: writeopiaManager.getStory(lastPos)?.nextPosition
                    ?: (lastPos + 1)
                cursorText to pos
            }
        }
    }

    private suspend fun Flow<ResultData<String>>.handleStream(
        writeopiaManager: WriteopiaStateManager,
        position: Double
    ) {
        this.onStart {
            writeopiaManager.loadingAtPosition(position)
        }.onCompletion {
            writeopiaManager.trackState()
        }.map { result ->
            when (result) {
                is ResultData.Complete -> result.data
                is ResultData.Error -> "Error. Message: ${result.exception?.message}"
                is ResultData.Loading,
                is ResultData.Idle,
                is ResultData.InProgress -> ""
            }
        }.collect { resultText ->
            writeopiaManager.changeStoryState(
                Action.StoryStateChange(
                    storyStep = StoryStep(
                        type = StoryTypes.AI_ANSWER.type,
                        text = resultText
                    ),
                    position = position,
                ),
                trackIt = false
            )
        }
    }
}
