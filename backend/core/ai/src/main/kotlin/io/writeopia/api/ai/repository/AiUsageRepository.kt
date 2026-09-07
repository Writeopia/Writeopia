package io.writeopia.api.ai.repository

import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Clock

data class AiUsageRecord(
    val id: String,
    val userId: String,
    val operationType: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val model: String,
    val createdAt: Long
)

data class AiUsageSummary(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalTokens: Long,
    val requestCount: Long
)

fun WriteopiaDbBackend.insertAiUsage(
    id: String,
    userId: String,
    operationType: String,
    inputTokens: Int,
    outputTokens: Int,
    totalTokens: Int,
    model: String
) {
    aiUsageEntityQueries.insert(
        id = id,
        user_id = userId,
        operation_type = operationType,
        input_tokens = inputTokens,
        output_tokens = outputTokens,
        total_tokens = totalTokens,
        model = model,
        created_at = Clock.System.now().toEpochMilliseconds()
    )
}

fun WriteopiaDbBackend.getAiUsageSummary(
    userId: String,
    fromTime: Long,
    toTime: Long
): AiUsageSummary {
    val result = aiUsageEntityQueries.sumByUserIdAndDateRange(userId, fromTime, toTime).executeAsOne()
    return AiUsageSummary(
        totalInputTokens = result.total_input_tokens.toLong(),
        totalOutputTokens = result.total_output_tokens.toLong(),
        totalTokens = result.total_tokens.toLong(),
        requestCount = result.request_count
    )
}
