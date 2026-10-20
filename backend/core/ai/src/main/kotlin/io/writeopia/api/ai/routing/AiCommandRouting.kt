package io.writeopia.api.ai.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.writeopia.api.ai.command.AiCommandPrompt
import io.writeopia.api.ai.model.AiRequestResult
import io.writeopia.api.ai.service.AiService
import io.writeopia.api.genai.model.AiGenerateRequest
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.api.typesafe.service.CommandIntentService
import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandIntentResponse
import io.writeopia.app.dto.ai.AiCommandRequest
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

private const val INTENT_OPERATION = "command-intent"
private const val COMMAND_OPERATION = "command"

/**
 * The intent of a free command: Jev decides whether the answer replaces the text the command is
 * about or is added after it. Gated like the other cloud AI routes: premium account and monthly
 * quota, unless in debug.
 */
fun Routing.commandIntentRoute(
    service: CommandIntentService,
    debugMode: Boolean = false,
    writeopiaDb: WriteopiaDbBackend? = null
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiCommandIntent()}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            val effectiveUserId = userId ?: if (debugMode) "debug-user" else null

            if (effectiveUserId == null) {
                call.respond(HttpStatusCode.Unauthorized, AiCommandIntentResponse(error = "User not authenticated"))
                return@post
            }

            if (!debugMode && writeopiaDb != null) {
                when (val authResult = AiService.checkAuthorization(effectiveUserId, INTENT_OPERATION, writeopiaDb)) {
                    is AiRequestResult.Forbidden -> {
                        call.respond(HttpStatusCode.Forbidden, AiCommandIntentResponse(error = authResult.message))
                        return@post
                    }

                    is AiRequestResult.QuotaExceeded -> {
                        call.respond(HttpStatusCode.TooManyRequests, AiCommandIntentResponse(error = authResult.message))
                        return@post
                    }

                    else -> {}
                }
            }

            val request = try {
                call.receive<AiCommandRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in command intent: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, AiCommandIntentResponse(error = "Invalid request format"))
                return@post
            }

            logger.info(
                "Command intent request - user: {}, command: {} chars, target: {} chars, context: {} chars",
                effectiveUserId,
                request.command.length,
                request.targetText.length,
                request.textBefore.length + request.textAfter.length
            )

            val (response, usage) = service.decide(request)

            if (response.error != null) {
                logger.warn("Command intent failed for user {}: {}", effectiveUserId, response.error)
                val status = if (request.command.isBlank()) HttpStatusCode.BadRequest else HttpStatusCode.ServiceUnavailable
                call.respond(status, response)
                return@post
            }

            if (usage.inputTokens > 0 || usage.outputTokens > 0) {
                AiService.saveUsage(
                    userId = effectiveUserId,
                    endpointName = INTENT_OPERATION,
                    modelName = "jev",
                    inputTokens = usage.inputTokens,
                    outputTokens = usage.outputTokens,
                    writeopiaDb = writeopiaDb
                )
            }

            call.respond(HttpStatusCode.OK, response)
        }
    }
}

/**
 * The answer of a free command, written by Gemini from the prompt [AiCommandPrompt] builds for
 * the intent the app sends (the one Jev decided, or ADD when it could not decide). Streams like
 * the other generation routes.
 */
fun Routing.aiCommandRoute(
    genAiService: GenAiService,
    debugMode: Boolean = false,
    writeopiaDb: WriteopiaDbBackend? = null
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiCommand()}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

            val request = try {
                call.receive<AiCommandRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in AI command: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, AiGenerateResponse(error = "Invalid request format"))
                return@post
            }

            if (request.command.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, AiGenerateResponse(error = "The command is empty"))
                return@post
            }

            val intent = request.intent ?: AiCommandIntent.ADD
            val prompt = AiCommandPrompt.forRequest(request, intent)

            logger.info(
                "AI command request - user: {}, intent: {}, target: {} chars, prompt: {} chars",
                userId,
                intent,
                request.targetText.length,
                prompt.length
            )

            respondAiRequest(
                request = AiGenerateRequest(prompt = prompt, model = request.model, stream = request.stream),
                endpointName = COMMAND_OPERATION,
                userId = userId,
                writeopiaDb = writeopiaDb,
                debugMode = debugMode,
                streamGenerator = { text, model, onUsage ->
                    genAiService.streamGenerateBaseWithUsage(text, model, onUsage)
                },
                syncGenerator = { text, model ->
                    genAiService.generateWithUsage(text, model)
                }
            )
        }
    }
}
