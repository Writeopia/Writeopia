package io.writeopia.api.genai.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)
