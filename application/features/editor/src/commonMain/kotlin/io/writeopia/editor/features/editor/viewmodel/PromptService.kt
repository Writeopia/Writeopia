package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.OllamaRepository
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

object PromptService {

    suspend fun documentPrompt(
        userId: String,
        targetMode: AiTargetMode,
        promptFn: (String, String, String) -> Flow<ResultData<String>>,
        writeopiaManager: WriteopiaStateManager,
        ollamaRepository: OllamaRepository
    ) {
        val (text, position) = getTextAndPosition(targetMode, writeopiaManager)

        val url = ollamaRepository.getConfiguredUrl(userId)?.trim()

        if (url == null) {
            writeopiaManager.changeStoryState(
                Action.StoryStateChange(
                    storyStep = StoryStep(
                        type = StoryTypes.AI_ANSWER.type,
                        text = "Ollama is not configured or not running."
                    ),
                    position = position,
                )
            )
        } else {
            val model = ollamaRepository.getSelectedModel(userId)
                ?: return

            promptFn(model, text, url).handleStream(writeopiaManager, position)
        }
    }

    suspend fun promptWithMode(
        userId: String,
        targetMode: AiTargetMode,
        writeopiaManager: WriteopiaStateManager,
        ollamaRepository: OllamaRepository
    ) {
        val (text, position) = getTextAndPosition(targetMode, writeopiaManager)
        prompt(userId, text, writeopiaManager, ollamaRepository, position)
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

    suspend fun prompt(
        userId: String,
        prompt: String?,
        writeopiaManager: WriteopiaStateManager,
        ollamaRepository: OllamaRepository,
        promptPosition: Double? = null
    ) {
        val position = promptPosition ?: writeopiaManager.getNextPosition()

        if (prompt != null && position != null) {
            val url = ollamaRepository.getConfiguredUrl(userId)?.trim()

            if (url == null) {
                writeopiaManager.changeStoryState(
                    Action.StoryStateChange(
                        storyStep = StoryStep(
                            type = StoryTypes.AI_ANSWER.type,
                            text = "Ollama is not configured or not running."
                        ),
                        position = position,
                    )
                )
            } else {
                val model = ollamaRepository.getSelectedModel(userId) ?: return

                ollamaRepository.streamReply(model, prompt, url)
                    .handleStream(writeopiaManager, position)
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
