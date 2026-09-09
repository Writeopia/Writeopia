package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

@Serializable
data class AiUsageResponse(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalTokens: Long,
    val requestCount: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val quota: Long
)
