package io.writeopia.api.ai.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.genai.model.AiGenerateRequest
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.connection.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Routing.aiRoute(debugMode: Boolean = false) {
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
        post("/api/ai/generate") {
            handleAiRequest(
                json = json,
                endpointName = "generate",
                streamGenerator = { prompt, model -> genAiService.streamGenerate(prompt, model) },
                syncGenerator = { prompt, model -> genAiService.generate(prompt, model) }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/summary") {
            handleAiRequest(
                json = json,
                endpointName = "summary",
                streamGenerator = { prompt, model -> genAiService.streamSummary(prompt, model) },
                syncGenerator = { prompt, model -> genAiService.generateSummary(prompt, model) }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/action-points") {
            handleAiRequest(
                json = json,
                endpointName = "action-points",
                streamGenerator = { prompt, model -> genAiService.streamActionPoints(prompt, model) },
                syncGenerator = { prompt, model -> genAiService.generateActionPoints(prompt, model) }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/faq") {
            handleAiRequest(
                json = json,
                endpointName = "faq",
                streamGenerator = { prompt, model -> genAiService.streamFaq(prompt, model) },
                syncGenerator = { prompt, model -> genAiService.generateFaq(prompt, model) }
            )
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/ai/tags") {
            handleAiRequest(
                json = json,
                endpointName = "tags",
                streamGenerator = { prompt, model -> genAiService.streamTags(prompt, model) },
                syncGenerator = { prompt, model -> genAiService.generateTags(prompt, model) }
            )
        }
    }
}

/**
 * Common handler for AI generation endpoints.
 * Handles request parsing, validation, and error responses consistently.
 */
private suspend fun RoutingContext.handleAiRequest(
    json: Json,
    endpointName: String,
    streamGenerator: (String, String?) -> Flow<AiGenerateResponse>,
    syncGenerator: suspend (String, String?) -> AiGenerateResponse
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

    // Process request
    try {
        if (request.stream) {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                streamGenerator(request.prompt, request.model)
                    .onEach { response ->
                        write("data: ${json.encodeToString(response)}\n\n")
                        flush()
                    }
                    .collect()
            }
        } else {
            val response = syncGenerator(request.prompt, request.model)
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
