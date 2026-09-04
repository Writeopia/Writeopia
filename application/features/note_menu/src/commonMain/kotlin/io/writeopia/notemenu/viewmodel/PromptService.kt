package io.writeopia.notemenu.viewmodel

import io.writeopia.LocalAiRepository

object PromptService {

    suspend fun prompt(
        userId: String,
        prompt: String,
        localAiRepository: LocalAiRepository,
        markdownResult: Boolean = false
    ): String? {
        val url = localAiRepository.getConfiguredUrl(userId)?.trim() ?: return null
        val model = localAiRepository.getSelectedModel(userId) ?: return null

        return localAiRepository.generateCompleteSummary(
            model = model,
            prompt = prompt,
            url = url,
            markdownResult = markdownResult
        )
    }
}
