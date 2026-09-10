package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

@Serializable
data class ModelTier(
    val name: String,
    val description: String,
    val modelName: String
)

@Serializable
data class LocalAiAutoConfigResponse(
    val ollamaUrl: String = "http://localhost:11434",
    val llmmanUrl: String = "http://localhost:17434",
    val modelTiers: List<ModelTier> = listOf(
        ModelTier("Light", "Fast and efficient for basic tasks", "gemma4:e4b"),
        ModelTier("Medium", "Balanced performance for most use cases", "gpt-oss:20b"),
        ModelTier("Heavy", "Maximum quality for complex tasks", "mistral-small:24b")
    ),
    val defaultTierIndex: Int = 1 // Medium pre-selected
)
