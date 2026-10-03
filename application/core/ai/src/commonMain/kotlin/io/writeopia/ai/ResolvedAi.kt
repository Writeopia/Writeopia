package io.writeopia.ai

import io.writeopia.LocalAiRepository
import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.model.AiProvider
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The AI that answers right now, with the provider it belongs to: the one the user picked, or the
 * one it fell back to because the picked one isn't ready.
 */
sealed class ResolvedAi(val provider: AiProvider) : AiStreaming {

    /** A model served on this machine, already configured with its server [url] and [model]. */
    class Local(
        val repository: LocalAiRepository,
        val url: String,
        val model: String,
    ) : ResolvedAi(AiProvider.LOCAL) {

        override fun stream(command: AiCommand, prompt: String): Flow<ResultData<String>> =
            when (command) {
                AiCommand.PROMPT -> repository.streamReply(model, prompt, url)
                AiCommand.SUMMARY -> repository.streamSummary(model, prompt, url)
                AiCommand.ACTION_POINTS -> repository.streamActionsPoints(model, prompt, url)
                AiCommand.FAQ -> repository.streamFaq(model, prompt, url)
                AiCommand.TAGS -> repository.streamTags(model, prompt, url)
            }
    }

    /**
     * The Writeopia backend. [repository] is null on a platform that only talks to the backend
     * through other APIs, like the documents API that summarizes whole documents.
     */
    class Cloud(val repository: GenAiRepository?) : ResolvedAi(AiProvider.CLOUD) {

        override fun stream(command: AiCommand, prompt: String): Flow<ResultData<String>> {
            val genAi = repository
                ?: return flowOf(ResultData.Error(Exception("Cloud AI is not available in this app")))

            return when (command) {
                AiCommand.PROMPT -> genAi.streamGenerate(prompt)
                AiCommand.SUMMARY -> genAi.streamSummary(prompt)
                AiCommand.ACTION_POINTS -> genAi.streamActionPoints(prompt)
                AiCommand.FAQ -> genAi.streamFaq(prompt)
                AiCommand.TAGS -> genAi.streamTags(prompt)
            }
        }
    }
}
