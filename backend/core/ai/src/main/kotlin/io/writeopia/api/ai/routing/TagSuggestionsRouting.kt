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
import io.writeopia.api.typesafe.service.TagSuggestionsService
import io.writeopia.app.dto.writingagent.TagSuggestionsRequest
import io.writeopia.app.dto.writingagent.TagSuggestionsResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

private const val OPERATION = "tag-suggestions"

/**
 * Tags a document: the apps send its text and the tags of the workspace catalogue, and get back
 * the tags Jev is confident the document should carry. Gated like the other cloud AI routes.
 */
fun Routing.tagSuggestionsRoute(
    service: TagSuggestionsService,
    debugMode: Boolean = false,
    writeopiaDb: WriteopiaDbBackend? = null
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiTagSuggestions()}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: if (debugMode) "debug-user" else null

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, TagSuggestionsResponse(error = "User not authenticated"))
                return@post
            }

            if (!debugMode && writeopiaDb != null) {
                when (val authResult = AiService.checkAuthorization(userId, OPERATION, writeopiaDb)) {
                    is AiRequestResult.Forbidden -> {
                        call.respond(HttpStatusCode.Forbidden, TagSuggestionsResponse(error = authResult.message))
                        return@post
                    }

                    is AiRequestResult.QuotaExceeded -> {
                        call.respond(HttpStatusCode.TooManyRequests, TagSuggestionsResponse(error = authResult.message))
                        return@post
                    }

                    else -> {}
                }
            }

            val request = try {
                call.receive<TagSuggestionsRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in tag suggestions: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, TagSuggestionsResponse(error = "Invalid request format"))
                return@post
            }

            logger.info(
                "Tag suggestions request - user: {}, document: {} chars, tags: {}",
                userId,
                request.documentText.length,
                request.tags.size
            )

            val (response, usage) = service.suggest(request)

            logger.info(
                "Tag suggestions answer - user: {}, tags: {}, tokens: {} in / {} out",
                userId,
                response.suggestions.joinToString { "${it.tagId}=${(it.probability * 100).toInt()}%" }.ifEmpty { "none" },
                usage.inputTokens,
                usage.outputTokens
            )

            if (response.error != null) {
                logger.warn("Tag suggestions failed for user {}: {}", userId, response.error)
                call.respond(HttpStatusCode.ServiceUnavailable, response)
                return@post
            }

            AiService.saveUsage(
                userId = userId,
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
