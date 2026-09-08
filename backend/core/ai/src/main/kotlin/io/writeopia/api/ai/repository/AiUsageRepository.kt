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

/**
 * Creates a token reservation by inserting a usage record with the reserved budget.
 * This ensures the reservation is counted in quota checks immediately.
 * Returns the reservation ID for later settlement or release.
 */
fun WriteopiaDbBackend.createTokenReservation(
    id: String,
    userId: String,
    operationType: String,
    reservedTokens: Int,
    model: String
) {
    aiUsageEntityQueries.insert(
        id = id,
        user_id = userId,
        operation_type = operationType,
        input_tokens = 0,
        output_tokens = 0,
        total_tokens = reservedTokens,
        model = model,
        created_at = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    )
}

/**
 * Settles a reservation by updating it with the actual token usage.
 */
fun WriteopiaDbBackend.settleTokenReservation(
    reservationId: String,
    inputTokens: Int,
    outputTokens: Int,
    totalTokens: Int
) {
    aiUsageEntityQueries.updateUsage(
        input_tokens = inputTokens,
        output_tokens = outputTokens,
        total_tokens = totalTokens,
        id = reservationId
    )
}

/**
 * Releases (deletes) a reservation, typically when the AI request fails.
 */
fun WriteopiaDbBackend.releaseTokenReservation(reservationId: String) {
    aiUsageEntityQueries.deleteById(reservationId)
}
