package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.id.GenerateId
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
        val text = when (targetMode) {
            AiTargetMode.DOCUMENT -> writeopiaManager.getDocumentText()
            AiTargetMode.SELECTED_LINES -> writeopiaManager.getCurrentSelectionText()
            AiTargetMode.CURSOR -> writeopiaManager.getCurrentText()
        }

        return text to answerPosition(targetMode, writeopiaManager)
    }

    /** Where an answer about the text [targetMode] points to goes: right after that text. */
    internal fun answerPosition(targetMode: AiTargetMode, writeopiaManager: WriteopiaStateManager): Double {
        val lastPos = writeopiaManager.lastPosition()
        val afterDocument = writeopiaManager.getStory(lastPos)?.nextPosition ?: (lastPos + 1)

        return when (targetMode) {
            AiTargetMode.DOCUMENT -> afterDocument
            AiTargetMode.SELECTED_LINES ->
                writeopiaManager.positionAfterSelection()
                    ?: writeopiaManager.getNextPosition()
                    ?: afterDocument
            AiTargetMode.CURSOR -> writeopiaManager.getNextPosition() ?: afterDocument
        }
    }

    /** Streams [answer] into the document at [position]: a spinner first, then the text as it comes. */
    internal suspend fun streamIntoDocument(
        answer: Flow<ResultData<String>>,
        writeopiaManager: WriteopiaStateManager,
        position: Double
    ) {
        answer.handleStream(writeopiaManager, position)
    }

    private suspend fun Flow<ResultData<String>>.handleStream(
        writeopiaManager: WriteopiaStateManager,
        position: Double
    ) {
        // One step for the whole answer, rewritten at each chunk. A new step per chunk would be
        // saved and synced one by one, and the ones before the last would outlive the answer.
        val answerId = GenerateId.generate()

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
                        id = answerId,
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
