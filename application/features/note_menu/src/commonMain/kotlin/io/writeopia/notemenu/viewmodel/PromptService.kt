package io.writeopia.notemenu.viewmodel

import io.writeopia.OllamaRepository

object PromptService {

    suspend fun prompt(
        userId: String,
        prompt: String,
        ollamaRepository: OllamaRepository
    ): String? {
        val url = ollamaRepository.getConfiguredUrl(userId)?.trim() ?: return null
        val model = ollamaRepository.getSelectedModel(userId) ?: return null

        return ollamaRepository.generateReply(model, prompt, url)
    }
}
