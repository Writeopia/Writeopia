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
import io.writeopia.api.genai.model.DocumentTagParser
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.app.dto.writingagent.TagCreateRequest
import io.writeopia.app.dto.writingagent.TagCreateResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

private const val OPERATION = "tag-create"

/**
 * Makes up a tag for a document: name and explanation, by the cloud AI, avoiding the names the
 * workspace catalogue already has. The app shows it for the user to confirm. Gated like the other
 * cloud AI routes.
 */
fun Routing.tagCreationRoute(
    genAiService: GenAiService,
    debugMode: Boolean = false,
    writeopiaDb: WriteopiaDbBackend? = null
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiTagCreate()}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: if (debugMode) "debug-user" else null

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, TagCreateResponse(error = "User not authenticated"))
                return@post
            }

            if (!debugMode && writeopiaDb != null) {
                when (val authResult = AiService.checkAuthorization(userId, OPERATION, writeopiaDb)) {
                    is AiRequestResult.Forbidden -> {
                        call.respond(HttpStatusCode.Forbidden, TagCreateResponse(error = authResult.message))
                        return@post
                    }

                    is AiRequestResult.QuotaExceeded -> {
                        call.respond(HttpStatusCode.TooManyRequests, TagCreateResponse(error = authResult.message))
                        return@post
                    }

                    else -> {}
                }
            }

            val request = try {
                call.receive<TagCreateRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in tag creation: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, TagCreateResponse(error = "Invalid request format"))
                return@post
            }

            if (request.documentText.isBlank() && request.documentTitle.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, TagCreateResponse(error = "The document has no text to tag"))
                return@post
            }

            logger.info(
                "Tag creation request - user: {}, document: {} chars, existing tags: {}",
                userId,
                request.documentText.length,
                request.existingTagNames.size
            )

            val (answer, usage) = genAiService.generateDocumentTagWithUsage(
                documentTitle = request.documentTitle,
                documentText = request.documentText,
                existingTagNames = request.existingTagNames,
            )

            if (answer.error != null) {
                logger.warn("Tag creation failed for user {}: {}", userId, answer.error)
                call.respond(HttpStatusCode.ServiceUnavailable, TagCreateResponse(error = answer.error))
                return@post
            }

            val tag = DocumentTagParser.parse(answer.response)
            if (tag == null) {
                logger.warn("Tag creation for user {} answered without a tag: {}", userId, answer.response?.take(200))
                call.respond(HttpStatusCode.ServiceUnavailable, TagCreateResponse(error = "The AI answered without a tag"))
                return@post
            }

            AiService.saveUsage(
                userId = userId,
                endpointName = OPERATION,
                modelName = "gemini",
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                writeopiaDb = writeopiaDb
            )

            logger.info("Tag creation answer - user: {}, tag: \"{}\"", userId, tag.name)
            call.respond(HttpStatusCode.OK, TagCreateResponse(name = tag.name, description = tag.description))
        }
    }
}
