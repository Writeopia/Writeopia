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
import io.writeopia.api.ai.folders.FolderCandidateRanker
import io.writeopia.api.ai.model.AiRequestResult
import io.writeopia.api.ai.service.AiService
import io.writeopia.api.typesafe.service.FolderSuggestionsService
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import io.writeopia.app.dto.writingagent.FolderSuggestionsResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

private const val OPERATION = "folder-suggestions"

/**
 * Files a document that was created in the root folder: the apps send its text and their
 * workspace, the backend picks the closest folders by their embeddings ([ranker]), and Jev
 * answers with the ones it is confident the document belongs to. Gated like the other cloud AI
 * routes.
 */
fun Routing.folderSuggestionsRoute(
    service: FolderSuggestionsService,
    debugMode: Boolean = false,
    writeopiaDb: WriteopiaDbBackend? = null,
    ranker: FolderCandidateRanker = FolderCandidateRanker(embeddings = null)
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiFolderSuggestions()}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: if (debugMode) "debug-user" else null

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, FolderSuggestionsResponse(error = "User not authenticated"))
                return@post
            }

            if (!debugMode && writeopiaDb != null) {
                when (val authResult = AiService.checkAuthorization(userId, OPERATION, writeopiaDb)) {
                    is AiRequestResult.Forbidden -> {
                        call.respond(HttpStatusCode.Forbidden, FolderSuggestionsResponse(error = authResult.message))
                        return@post
                    }

                    is AiRequestResult.QuotaExceeded -> {
                        call.respond(HttpStatusCode.TooManyRequests, FolderSuggestionsResponse(error = authResult.message))
                        return@post
                    }

                    else -> {}
                }
            }

            val request = try {
                call.receive<FolderSuggestionsRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in folder suggestions: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, FolderSuggestionsResponse(error = "Invalid request format"))
                return@post
            }

            val candidates = ranker.candidates(request, writeopiaDb)

            logger.info(
                "Folder suggestions request - user: {}, document: {} chars, folders: {} from {}: {}",
                userId,
                request.documentText.length,
                candidates.candidates.size,
                candidates.source,
                candidates.candidates.joinToString { folder -> folder.name }
            )

            val (response, usage) = service.suggest(request, candidates.candidates)

            logger.info(
                "Folder suggestions answer - user: {}, folders: {}, tokens: {} in / {} out",
                userId,
                response.suggestions.joinToString { "${it.folderId}=${(it.probability * 100).toInt()}%" }.ifEmpty { "none" },
                usage.inputTokens,
                usage.outputTokens
            )

            if (response.error != null) {
                logger.warn("Folder suggestions failed for user {}: {}", userId, response.error)
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
