package io.writeopia.api.ai.service

import io.writeopia.api.ai.model.AiRequestResult
import io.writeopia.api.ai.repository.getAiUsageSummary
import io.writeopia.api.ai.repository.insertAiUsage
import io.writeopia.api.genai.model.AiGenerateRequest
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import java.util.UUID

object AiService {
    private const val ACCOUNT_TYPE_PREMIUM = "PREMIUM"
    private const val MONTHLY_TOKEN_QUOTA = 100_000L
    private val json = Json { encodeDefaults = true }

    suspend fun processAiRequest(
        request: AiGenerateRequest,
        userId: String?,
        endpointName: String,
        writeopiaDb: WriteopiaDbBackend?,
        debugMode: Boolean,
        streamGenerator: (String, String?, (TokenUsage) -> Unit) -> Flow<AiGenerateResponse>,
        syncGenerator: suspend (String, String?) -> Pair<AiGenerateResponse, TokenUsage>
    ): AiRequestResult {
        // Validate prompt
        if (request.prompt.isBlank()) {
            return AiRequestResult.InvalidRequest("Prompt cannot be empty")
        }

        val effectiveUserId = userId ?: if (debugMode) "debug-user" else null
        val modelName = request.model ?: "gemini-2.0-flash"

        // Authorization checks (skip in debug mode)
        if (!debugMode && effectiveUserId != null && writeopiaDb != null) {
            val authResult = checkAuthorization(
                effectiveUserId,
                endpointName,
                writeopiaDb
            )
            if (authResult != null) return authResult
        }

        // Process request
        return try {
            logger.info(
                "AI {} request - user: {}, stream: {}, db available: {}",
                endpointName, effectiveUserId, request.stream, writeopiaDb != null
            )

            if (request.stream) {
                handleStreamingRequest(
                    request,
                    effectiveUserId,
                    endpointName,
                    modelName,
                    writeopiaDb,
                    streamGenerator
                )
            } else {
                handleSyncRequest(
                    request,
                    effectiveUserId,
                    endpointName,
                    modelName,
                    writeopiaDb,
                    syncGenerator
                )
            }
        } catch (e: Exception) {
            logger.error("Error in AI {} endpoint", endpointName, e)
            AiRequestResult.Error("An unexpected error occurred")
        }
    }

    private fun checkAuthorization(
        userId: String,
        endpointName: String,
        writeopiaDb: WriteopiaDbBackend
    ): AiRequestResult? {
        // Check if user has premium account
        val accountType = writeopiaDb.userEntityQueries
            .selectAccountTypeById(userId)
            .executeAsOneOrNull()

        if (accountType != ACCOUNT_TYPE_PREMIUM) {
            logger.info(
                "AI {} request denied - user {} is not premium (type: {})",
                endpointName, userId, accountType
            )
            return AiRequestResult.Forbidden("Cloud AI requires a premium subscription")
        }

        // Check quota
        val now = Clock.System.now()
        val startOfMonth = now.toLocalDateTime(TimeZone.UTC)
            .startOfMonthLocal()
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds()

        val currentUsage = writeopiaDb.getAiUsageSummary(
            userId,
            startOfMonth,
            now.toEpochMilliseconds()
        )

        if (currentUsage.totalTokens >= MONTHLY_TOKEN_QUOTA) {
            logger.info(
                "AI {} request denied - user {} exceeded quota ({}/{})",
                endpointName, userId, currentUsage.totalTokens, MONTHLY_TOKEN_QUOTA
            )
            return AiRequestResult.QuotaExceeded("Monthly token quota exceeded")
        }

        logger.info(
            "AI {} authorization passed - user: {}, accountType: {}, usage: {}/{}",
            endpointName, userId, accountType, currentUsage.totalTokens, MONTHLY_TOKEN_QUOTA
        )

        return null
    }

    private suspend fun handleStreamingRequest(
        request: AiGenerateRequest,
        userId: String?,
        endpointName: String,
        modelName: String,
        writeopiaDb: WriteopiaDbBackend?,
        streamGenerator: (String, String?, (TokenUsage) -> Unit) -> Flow<AiGenerateResponse>
    ): AiRequestResult {
        var streamTokenUsage: TokenUsage? = null

        val flow = streamGenerator(request.prompt, request.model) { usage ->
            logger.info(
                "AI {} streaming usage callback - input: {}, output: {}, total: {}",
                endpointName, usage.inputTokens, usage.outputTokens, usage.totalTokens
            )
            streamTokenUsage = usage
        }.map { response ->
            json.encodeToString(AiGenerateResponse.serializer(), response)
        }

        val onComplete: suspend () -> TokenUsage? = {
            logger.info(
                "AI {} stream complete - captured usage: {}",
                endpointName,
                streamTokenUsage
            )
            streamTokenUsage?.let { tokenUsage ->
                saveUsage(userId, endpointName, modelName, tokenUsage, writeopiaDb)
            }
            streamTokenUsage
        }

        return AiRequestResult.StreamSuccess(flow, onComplete)
    }

    private suspend fun handleSyncRequest(
        request: AiGenerateRequest,
        userId: String?,
        endpointName: String,
        modelName: String,
        writeopiaDb: WriteopiaDbBackend?,
        syncGenerator: suspend (String, String?) -> Pair<AiGenerateResponse, TokenUsage>
    ): AiRequestResult {
        val (response, tokenUsage) = syncGenerator(request.prompt, request.model)
        logger.info(
            "AI {} sync response - input: {}, output: {}, total: {}, error: {}",
            endpointName,
            tokenUsage.inputTokens,
            tokenUsage.outputTokens,
            tokenUsage.totalTokens,
            response.error
        )

        // Save usage to database if successful
        val error = response.error
        if (error == null) {
            saveUsage(userId, endpointName, modelName, tokenUsage, writeopiaDb)
        }

        return if (error != null) {
            AiRequestResult.Error(error)
        } else {
            AiRequestResult.Success(response, tokenUsage)
        }
    }

    private suspend fun saveUsage(
        userId: String?,
        endpointName: String,
        modelName: String,
        tokenUsage: TokenUsage,
        writeopiaDb: WriteopiaDbBackend?
    ) {
        if (userId != null && writeopiaDb != null && tokenUsage.totalTokens > 0) {
            try {
                logger.info(
                    "AI {} saving usage - user: {}, tokens: {}",
                    endpointName, userId, tokenUsage.totalTokens
                )
                writeopiaDb.insertAiUsage(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    operationType = endpointName,
                    inputTokens = tokenUsage.inputTokens,
                    outputTokens = tokenUsage.outputTokens,
                    totalTokens = tokenUsage.totalTokens,
                    model = modelName
                )
                logger.info(
                    "AI {} usage saved successfully for user {}",
                    endpointName,
                    userId
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to save AI usage for user {}: {}",
                    userId,
                    e.message
                )
            }
        }
    }

    private fun LocalDateTime.startOfMonthLocal() =
        LocalDateTime(year, month, 1, 0, 0, 0, 0)
}
