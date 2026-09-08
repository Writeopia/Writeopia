package io.writeopia.api.ai.model

import kotlinx.serialization.Serializable

/**
 * Public configuration used by clients to auto-configure Local AI.
 *
 * Ollama and llmman (https://github.com/llmmanorg/llmman) run on the user's machine and both
 * expose an Ollama-compatible API; only the port differs. Keeping the ports and default model
 * here (instead of hardcoded in every client) lets them be changed without an app release.
 */
@Serializable
data class LocalAiAutoConfigResponse(
    val ollamaUrl: String = "http://localhost:11434",
    val llmmanUrl: String = "http://localhost:17434",
    val defaultModel: String = "gemma4"
)
