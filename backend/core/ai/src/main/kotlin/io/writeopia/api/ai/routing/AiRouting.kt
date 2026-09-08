package io.writeopia.api.ai.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.ai.repository.insertAiUsage
import io.writeopia.api.ai.repository.getAiUsageSummary
import io.writeopia.api.genai.model.AiGenerateRequest
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class AiUsageResponse(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalTokens: Long,
    val requestCount: Long,
    val periodStart: Long,
    val periodEnd: Long
)

fun Routing.aiRoute(debugMode: Boolean = false, writeopiaDb: WriteopiaDbBackend? = null) {
    val genAiService = GenAiService()
    val json = Json { encodeDefaults = true }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/ai/status") {
            val available = genAiService.isAvailable()
            call.respond(
                HttpStatusCode.OK,
                mapOf("available" to available)
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/ai/usage") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

            if (userId == null && !debugMode) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User not authenticated"))
                return@get
            }

            val effectiveUserId = userId ?: "debug-user"

            // Get usage for current month
            val now = Clock.System.now()
            val localDateTime = now.toLocalDateTime(TimeZone.UTC)
            val startOfMonthLocal = LocalDateTime(
                localDateTime.year,
                localDateTime.month,
                1,
                0,
                0,
                0,
                0
            )
            val startOfMonth = startOfMonthLocal.toInstant(TimeZone.UTC).toEpochMilliseconds()

            if (writeopiaDb != null) {
                logger.info("AI usage query - user: {}, from: {}, to: {}",
                    effectiveUserId, startOfMonth, now.toEpochMilliseconds())

                val summary = writeopiaDb.getAiUsageSummary(
                    effectiveUserId,
                    startOfMonth,
                    now.toEpochMilliseconds()
                )

                logger.info("AI usage result - user: {}, totalTokens: {}, requests: {}",
                    effectiveUserId, summary.totalTokens, summary.requestCount)

                call.respond(
                    HttpStatusCode.OK,
                    AiUsageResponse(
                        totalInputTokens = summary.totalInputTokens,
                        totalOutputTokens = summary.totalOutputTokens,
                        totalTokens = summary.totalTokens,
                        requestCount = summary.requestCount,
                        periodStart = startOfMonth,
                        periodEnd = now.toEpochMilliseconds()
                    )
                )
            } else {
                logger.warn("AI usage query - database not available")
                call.respond(
                    HttpStatusCode.OK,
                    AiUsageResponse(
                        totalInputTokens = 0,
                        totalOutputTokens = 0,
                        totalTokens = 0,
                        requestCount = 0,
                        periodStart = startOfMonth,
                        periodEnd = now.toEpochMilliseconds()
                    )
                )
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/generate") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequestWithUsage(
                json = json,
                endpointName = "generate",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { prompt, model, onUsage -> genAiService.streamGenerateBaseWithUsage(prompt, model, onUsage) },
                syncGenerator = { prompt, model -> genAiService.generateWithUsage(prompt, model) }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/summary") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequestWithUsage(
                json = json,
                endpointName = "summary",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { prompt, model, onUsage -> genAiService.streamSummaryWithUsage(prompt, model, onUsage) },
                syncGenerator = { prompt, model -> genAiService.generateSummaryWithUsage(prompt, model) }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/action-points") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequestWithUsage(
                json = json,
                endpointName = "action-points",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { prompt, model, onUsage -> genAiService.streamActionPointsWithUsage(prompt, model, onUsage) },
                syncGenerator = { prompt, model -> genAiService.generateActionPointsWithUsage(prompt, model) }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/faq") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequestWithUsage(
                json = json,
                endpointName = "faq",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { prompt, model, onUsage -> genAiService.streamFaqWithUsage(prompt, model, onUsage) },
                syncGenerator = { prompt, model -> genAiService.generateFaqWithUsage(prompt, model) }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/tags") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequestWithUsage(
                json = json,
                endpointName = "tags",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { prompt, model, onUsage -> genAiService.streamTagsWithUsage(prompt, model, onUsage) },
                syncGenerator = { prompt, model -> genAiService.generateTagsWithUsage(prompt, model) }
            )
        }
    }
}

/**
 * Common handler for AI generation endpoints with usage tracking.
 * Handles request parsing, validation, error responses, and saves usage to database.
 */
private suspend fun RoutingContext.handleAiRequestWithUsage(
    json: Json,
    endpointName: String,
    userId: String?,
    writeopiaDb: WriteopiaDbBackend?,
    debugMode: Boolean,
    streamGenerator: (String, String?, (TokenUsage) -> Unit) -> Flow<AiGenerateResponse>,
    syncGenerator: suspend (String, String?) -> Pair<AiGenerateResponse, TokenUsage>
) {
    // Parse request - return 400 for malformed JSON
    val request = try {
        call.receive<AiGenerateRequest>()
    } catch (e: ContentTransformationException) {
        logger.warn("Bad request in AI {} endpoint: {}", endpointName, e::class.simpleName)
        call.respond(
            HttpStatusCode.BadRequest,
            AiGenerateResponse(error = "Invalid request format")
        )
        return
    }

    // Validate prompt is not empty
    if (request.prompt.isBlank()) {
        call.respond(
            HttpStatusCode.BadRequest,
            AiGenerateResponse(error = "Prompt cannot be empty")
        )
        return
    }

    val effectiveUserId = userId ?: if (debugMode) "debug-user" else null
    val modelName = request.model ?: "gemini-2.0-flash"

    // Process request
    try {
        logger.info("AI {} request - user: {}, stream: {}, db available: {}",
            endpointName, effectiveUserId, request.stream, writeopiaDb != null)

        if (request.stream) {
            // Track usage from streaming response
            var streamTokenUsage: TokenUsage? = null

            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                streamGenerator(request.prompt, request.model) { usage ->
                    logger.info("AI {} streaming usage callback - input: {}, output: {}, total: {}",
                        endpointName, usage.inputTokens, usage.outputTokens, usage.totalTokens)
                    streamTokenUsage = usage
                }
                    .onEach { response ->
                        write("data: ${json.encodeToString(response)}\n\n")
                        flush()
                    }
                    .collect()
            }

            // Save streaming usage to database after stream completes
            logger.info("AI {} stream complete - captured usage: {}", endpointName, streamTokenUsage)
            streamTokenUsage?.let { tokenUsage ->
                logger.info("AI {} saving streaming usage - user: {}, tokens: {}, db: {}",
                    endpointName, effectiveUserId, tokenUsage.totalTokens, writeopiaDb != null)
                if (effectiveUserId != null && writeopiaDb != null && tokenUsage.totalTokens > 0) {
                    try {
                        writeopiaDb.insertAiUsage(
                            id = UUID.randomUUID().toString(),
                            userId = effectiveUserId,
                            operationType = endpointName,
                            inputTokens = tokenUsage.inputTokens,
                            outputTokens = tokenUsage.outputTokens,
                            totalTokens = tokenUsage.totalTokens,
                            model = modelName
                        )
                        logger.info("AI {} usage saved successfully for user {}", endpointName, effectiveUserId)
                    } catch (e: Exception) {
                        logger.error("Failed to save streaming AI usage for user {}: {}", effectiveUserId, e.message)
                    }
                }
            }
        } else {
            val (response, tokenUsage) = syncGenerator(request.prompt, request.model)
            logger.info("AI {} sync response - input: {}, output: {}, total: {}, error: {}",
                endpointName, tokenUsage.inputTokens, tokenUsage.outputTokens, tokenUsage.totalTokens, response.error)

            // Save usage to database if successful
            if (response.error == null && effectiveUserId != null && writeopiaDb != null) {
                try {
                    writeopiaDb.insertAiUsage(
                        id = UUID.randomUUID().toString(),
                        userId = effectiveUserId,
                        operationType = endpointName,
                        inputTokens = tokenUsage.inputTokens,
                        outputTokens = tokenUsage.outputTokens,
                        totalTokens = tokenUsage.totalTokens,
                        model = modelName
                    )
                    logger.info("AI {} usage saved successfully for user {}", endpointName, effectiveUserId)
                } catch (e: Exception) {
                    logger.error("Failed to save AI usage for user {}: {}", effectiveUserId, e.message)
                }
            }

            if (response.error != null) {
                call.respond(HttpStatusCode.InternalServerError, response)
            } else {
                call.respond(HttpStatusCode.OK, response)
            }
        }
    } catch (e: Exception) {
        logger.error("Error in AI {} endpoint", endpointName, e)
        // Only respond if the response hasn't been committed (e.g., during streaming)
        if (!call.response.isCommitted) {
            call.respond(
                HttpStatusCode.InternalServerError,
                AiGenerateResponse(error = "An unexpected error occurred")
            )
        }
    }
}
