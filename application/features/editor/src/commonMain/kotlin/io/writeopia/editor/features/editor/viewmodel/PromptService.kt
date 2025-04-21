package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.OllamaRepository
import io.writeopia.common.utils.ResultData
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

object PromptService {

    suspend fun documentPrompt(
        promptFn: (String, String, String) -> Flow<ResultData<String>>,
        writeopiaManager: WriteopiaStateManager,
        ollamaRepository: OllamaRepository
    ) {
        val text = writeopiaManager.getCurrentSelectionText()
            ?: writeopiaManager.getDocumentText()

        val position =
            writeopiaManager.positionAfterSelection() ?: writeopiaManager.lastPosition()

        val url = ollamaRepository.getConfiguredOllamaUrl()?.trim()

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
            val model = ollamaRepository.getOllamaSelectedModel("disconnected_user")
                ?: return

            promptFn(model, text, url).handleStream(writeopiaManager, position)
        }
    }

    suspend fun promptBySelection(
        writeopiaManager: WriteopiaStateManager,
        ollamaRepository: OllamaRepository
    ) {
        val text = writeopiaManager.getCurrentText()

        prompt(text, writeopiaManager, ollamaRepository)
    }

    suspend fun prompt(
        prompt: String?,
        writeopiaManager: WriteopiaStateManager,
        ollamaRepository: OllamaRepository,
        promptPosition: Int? = null
    ) {
        val position = promptPosition ?: writeopiaManager.getNextPosition()

        if (prompt != null && position != null) {
            val url = ollamaRepository.getConfiguredOllamaUrl()?.trim()

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
                val model = ollamaRepository.getOllamaSelectedModel("disconnected_user")
                    ?: return

                ollamaRepository.streamReply(model, prompt, url)
                    .handleStream(writeopiaManager, position)
            }
        }
    }

    private suspend fun Flow<ResultData<String>>.handleStream(
        writeopiaManager: WriteopiaStateManager,
        position: Int
    ) {
        this.onStart {
            writeopiaManager.addAtPosition(
                storyStep = StoryStep(
                    type = StoryTypes.LOADING.type,
                    ephemeral = true
                ),
                position = position
            )
        }
            .onCompletion {
                writeopiaManager.trackState()
            }
            .map { result ->
                when (result) {
                    is ResultData.Complete -> result.data
                    is ResultData.Error -> "Error. Message: ${result.exception.message}"
                    is ResultData.Loading,
                    is ResultData.Idle,
                    is ResultData.InProgress -> ""
                }
            }
            .collect { resultText ->
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
