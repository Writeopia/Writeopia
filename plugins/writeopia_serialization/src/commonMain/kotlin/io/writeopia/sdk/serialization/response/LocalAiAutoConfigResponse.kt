package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

@Serializable
enum class ModelTierType {
    LIGHT,
    MEDIUM,
    HEAVY
}

@Serializable
data class ModelTier(
    val type: ModelTierType,
    val modelName: String
)

@Serializable
data class LocalAiAutoConfigResponse(
    val ollamaUrl: String = "http://localhost:11434",
    val llmmanUrl: String = "http://localhost:17434",
    val modelTiers: List<ModelTier> = listOf(
        ModelTier(ModelTierType.LIGHT, "gemma4:e4b"),
        ModelTier(ModelTierType.MEDIUM, "gpt-oss:20b"),
        ModelTier(ModelTierType.HEAVY, "mistral-small:24b")
    ),
    // Medium pre-selected
    val defaultTierIndex: Int = 1
)
