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
import io.writeopia.api.ai.model.AiRequestResult
import io.writeopia.api.ai.service.AiService
import io.writeopia.api.typesafe.service.WritingSuggestionsService
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import io.writeopia.app.dto.writingagent.WritingSuggestionsResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

private const val OPERATION = "writing-suggestions"

/**
 * The writing agent. The apps send the paragraph being written (or the document that just
 * opened) and get back the editor actions Jev is confident the writer wants. The TypeSafe key
 * never leaves the backend.
 *
 * It is gated like the other cloud AI routes: premium account and monthly quota, unless in debug.
 */
fun Routing.writingSuggestionsRoute(
    service: WritingSuggestionsService,
    debugMode: Boolean = false,
    writeopiaDb: WriteopiaDbBackend? = null
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiWritingSuggestions()}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            val effectiveUserId = userId ?: if (debugMode) "debug-user" else null

            if (effectiveUserId == null) {
                call.respond(HttpStatusCode.Unauthorized, WritingSuggestionsResponse(error = "User not authenticated"))
                return@post
            }

            if (!debugMode && writeopiaDb != null) {
                when (val authResult = AiService.checkAuthorization(effectiveUserId, OPERATION, writeopiaDb)) {
                    is AiRequestResult.Forbidden -> {
                        call.respond(HttpStatusCode.Forbidden, WritingSuggestionsResponse(error = authResult.message))
                        return@post
                    }

                    is AiRequestResult.QuotaExceeded -> {
                        call.respond(HttpStatusCode.TooManyRequests, WritingSuggestionsResponse(error = authResult.message))
                        return@post
                    }

                    else -> {}
                }
            }

            val request = try {
                call.receive<WritingSuggestionsRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in writing suggestions: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, WritingSuggestionsResponse(error = "Invalid request format"))
                return@post
            }

            logger.info(
                "Writing suggestions {} request - user: {}, paragraph: {} chars, document: {} chars, block: {}",
                request.scope,
                effectiveUserId,
                request.text.length,
                request.documentText?.length ?: 0,
                request.blockType
            )

            val (response, usage) = service.suggest(request)

            logger.info(
                "Writing suggestions {} answer - user: {}, suggestions: {}, tokens: {} in / {} out",
                request.scope,
                effectiveUserId,
                response.suggestions.joinToString { "${it.action}=${(it.probability * 100).toInt()}%" }
                    .ifEmpty { "none" },
                usage.inputTokens,
                usage.outputTokens
            )

            if (response.error != null) {
                logger.warn("Writing suggestions failed for user {}: {}", effectiveUserId, response.error)
                call.respond(HttpStatusCode.ServiceUnavailable, response)
                return@post
            }

            AiService.saveUsage(
                userId = effectiveUserId,
                endpointName = OPERATION,
                modelName = "jev",
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                writeopiaDb = writeopiaDb
            )

            call.respond(HttpStatusCode.OK, response)
        }
    }
}
