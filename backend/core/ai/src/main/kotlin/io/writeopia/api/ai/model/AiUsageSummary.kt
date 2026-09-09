package io.writeopia.api.ai.model

data class AiUsageSummary(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalTokens: Long,
    val requestCount: Long
)
