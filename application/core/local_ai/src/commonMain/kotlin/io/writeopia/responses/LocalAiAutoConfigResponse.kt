package io.writeopia.responses

import kotlinx.serialization.Serializable

/**
 * Configuration served by the Writeopia backend to auto-configure Local AI (Ollama / llmman).
 * Mirrors `io.writeopia.api.ai.model.LocalAiAutoConfigResponse` on the backend.
 */
@Serializable
data class LocalAiAutoConfigResponse(
    val ollamaUrl: String = "",
    val llmmanUrl: String = "",
    val defaultModel: String = ""
)
