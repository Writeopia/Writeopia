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
import io.writeopia.api.ai.config.AiConfig
import io.writeopia.api.ai.model.AiRequestResult
import io.writeopia.api.ai.repository.getAiUsageSummary
import io.writeopia.api.ai.service.AiService
import io.writeopia.api.genai.model.AiGenerateRequest
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.connection.logger
import io.writeopia.connection.startOfMonth
import io.writeopia.connection.toEpochMillisUtc
import io.writeopia.sdk.serialization.response.AiUsageResponse
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun Routing.aiRoute(debugMode: Boolean = false, writeopiaDb: WriteopiaDbBackend? = null) {
    // In production mode, database is required for premium/quota enforcement
    if (!debugMode && writeopiaDb == null) {
        throw IllegalStateException(
            "WriteopiaDbBackend is required for AI routes in production mode. " +
                "Premium and quota checks cannot be enforced without a database."
        )
    }

    val genAiService = GenAiService()

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
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "User not authenticated")
                )
                return@get
            }

            val effectiveUserId = userId ?: "debug-user"

            // Get usage for current month
            val now = Clock.System.now()
            val startOfMonth = now.toLocalDateTime(TimeZone.UTC)
                .startOfMonth()
                .toEpochMillisUtc()

            if (writeopiaDb != null) {
                logger.info(
                    "AI usage query - user: {}, from: {}, to: {}",
                    effectiveUserId, startOfMonth, now.toEpochMilliseconds()
                )

                val summary = writeopiaDb.getAiUsageSummary(
                    effectiveUserId,
                    startOfMonth,
                    now.toEpochMilliseconds()
                )

                logger.info(
                    "AI usage result - user: {}, totalTokens: {}, requests: {}",
                    effectiveUserId, summary.totalTokens, summary.requestCount
                )

                call.respond(
                    HttpStatusCode.OK,
                    AiUsageResponse(
                        totalInputTokens = summary.totalInputTokens,
                        totalOutputTokens = summary.totalOutputTokens,
                        totalTokens = summary.totalTokens,
                        requestCount = summary.requestCount,
                        periodStart = startOfMonth,
                        periodEnd = now.toEpochMilliseconds(),
                        quota = AiConfig.monthlyTokenQuota()
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
                        periodEnd = now.toEpochMilliseconds(),
                        quota = AiConfig.monthlyTokenQuota()
                    )
                )
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/generate") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequest(
                endpointName = "generate",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { prompt, model, onUsage ->
                    genAiService.streamGenerateBaseWithUsage(prompt, model, onUsage)
                },
                syncGenerator = { prompt, model ->
                    genAiService.generateWithUsage(prompt, model)
                }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/summary") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequest(
                endpointName = "summary",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = genAiService::streamSummaryWithUsage,
                syncGenerator = { prompt, model ->
                    genAiService.generateSummaryWithUsage(prompt, model)
                }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/action-points") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequest(
                endpointName = "action-points",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = genAiService::streamActionPointsWithUsage,
                syncGenerator = genAiService::generateActionPointsWithUsage
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/faq") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequest(
                endpointName = "faq",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { prompt, model, onUsage ->
                    genAiService.streamFaqWithUsage(prompt, model, onUsage)
                },
                syncGenerator = { prompt, model ->
                    genAiService.generateFaqWithUsage(prompt, model)
                }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/tags") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            handleAiRequest(
                endpointName = "tags",
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { prompt, model, onUsage ->
                    genAiService.streamTagsWithUsage(prompt, model, onUsage)
                },
                syncGenerator = { prompt, model ->
                    genAiService.generateTagsWithUsage(prompt, model)
                }
            )
        }
    }
}

/**
 * Common handler for AI generation endpoints.
 * Delegates business logic to AiService and maps results to HTTP responses.
 */
private suspend fun RoutingContext.handleAiRequest(
    endpointName: String,
    userId: String?,
    writeopiaDb: WriteopiaDbBackend?,
    debugMode: Boolean,
    streamGenerator: (String, String?, (TokenUsage) -> Unit) -> Flow<AiGenerateResponse>,
    syncGenerator: suspend (String, String?) -> Pair<AiGenerateResponse, TokenUsage>
) {
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

    val result = AiService.processAiRequest(
        request = request,
        userId = userId,
        endpointName = endpointName,
        writeopiaDb = writeopiaDb,
        debugMode = debugMode,
        streamGenerator = streamGenerator,
        syncGenerator = syncGenerator
    )

    val handleError = suspend { code: HttpStatusCode, message: String ->
        call.respond(code, AiGenerateResponse(error = message))
    }

    when (result) {
        is AiRequestResult.Success -> {
            call.respond(HttpStatusCode.OK, result.response)
        }
        is AiRequestResult.StreamSuccess -> {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                result.flow
                    .onEach { encodedResponse ->
                        write("data: $encodedResponse\n\n")
                        flush()
                    }
                    .onCompletion {
                        result.onComplete()
                    }
                    .collect()
            }
        }
        is AiRequestResult.InvalidRequest -> {
            handleError(HttpStatusCode.BadRequest, result.message)
        }
        is AiRequestResult.Unauthorized -> {
            handleError(HttpStatusCode.Unauthorized, result.message)
        }
        is AiRequestResult.Forbidden -> {
            handleError(HttpStatusCode.Forbidden, result.message)
        }
        is AiRequestResult.QuotaExceeded -> {
            handleError(HttpStatusCode.TooManyRequests, result.message)
        }
        is AiRequestResult.Error -> {
            handleError(HttpStatusCode.InternalServerError, result.message)
        }
    }
}
