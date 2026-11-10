package io.writeopia.api.ai.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.writeopia.api.ai.model.AiRequestResult
import io.writeopia.api.ai.service.AiService
import io.writeopia.api.ai.service.EquationResult
import io.writeopia.api.ai.service.EquationService
import io.writeopia.app.dto.ai.EquationRecognizeRequest
import io.writeopia.app.dto.ai.EquationRenderRequest
import io.writeopia.app.dto.ai.EquationResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

private const val RECOGNIZE_ENDPOINT = "equation-recognize"

/** Characters of LaTeX a render accepts; an equation is a line or a few, not a document. */
internal const val MAX_LATEX_LENGTH = 4_000

/** Characters of drawing JSON a recognition accepts: about 50,000 points. */
internal const val MAX_DRAWING_LENGTH = 2_000_000

/**
 * Equations: the cloud AI reads one in a drawing (premium and quota, like the other AI routes),
 * and the backend renders LaTeX to a picture (any signed-in user; no AI involved).
 */
fun Routing.equationRoute(
    equationService: EquationService,
    debugMode: Boolean,
    writeopiaDb: WriteopiaDbBackend?
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiEquationRecognize()}") {
            val userId = userIdOrRespond(debugMode) ?: return@post

            if (!debugMode && writeopiaDb != null) {
                when (val denied = AiService.checkAuthorization(userId, RECOGNIZE_ENDPOINT, writeopiaDb)) {
                    null -> Unit
                    is AiRequestResult.Forbidden -> {
                        call.respond(HttpStatusCode.Forbidden, EquationResponse(error = denied.message))
                        return@post
                    }
                    is AiRequestResult.QuotaExceeded -> {
                        call.respond(HttpStatusCode.TooManyRequests, EquationResponse(error = denied.message))
                        return@post
                    }
                    else -> {
                        call.respond(HttpStatusCode.Forbidden, EquationResponse(error = "Not allowed"))
                        return@post
                    }
                }
            }

            val request = try {
                call.receive<EquationRecognizeRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in the equation recognize endpoint: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, EquationResponse(error = "Invalid request format"))
                return@post
            }

            if (request.drawing.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, EquationResponse(error = "The drawing is empty"))
                return@post
            }

            if (request.drawing.length > MAX_DRAWING_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, EquationResponse(error = "The drawing is too large"))
                return@post
            }

            val started = System.currentTimeMillis()
            val result = equationService.recognize(request.drawing)
            logger.info(
                "Equation recognized - user: {}, result: {}, tokens: {}, {} ms",
                userId, result::class.simpleName, result.tokenUsage.totalTokens, System.currentTimeMillis() - started
            )

            // The model answered, so its tokens count whatever came out of them.
            AiService.saveUsage(
                userId = userId,
                endpointName = RECOGNIZE_ENDPOINT,
                modelName = "equation",
                inputTokens = result.tokenUsage.inputTokens,
                outputTokens = result.tokenUsage.outputTokens,
                writeopiaDb = writeopiaDb
            )

            respond(result)
        }

        post("/${EndPoints.aiEquationRender()}") {
            userIdOrRespond(debugMode) ?: return@post

            val request = try {
                call.receive<EquationRenderRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in the equation render endpoint: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, EquationResponse(error = "Invalid request format"))
                return@post
            }

            if (request.latex.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, EquationResponse(error = "The equation is empty"))
                return@post
            }

            if (request.latex.length > MAX_LATEX_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, EquationResponse(error = "The equation is too long"))
                return@post
            }

            respond(equationService.render(request.latex))
        }
    }
}

private suspend fun RoutingContext.userIdOrRespond(debugMode: Boolean): String? {
    val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?: if (debugMode) "debug-user" else null

    if (userId == null) {
        call.respond(HttpStatusCode.Unauthorized, EquationResponse(error = "User not authenticated"))
    }

    return userId
}

private suspend fun RoutingContext.respond(result: EquationResult) {
    when (result) {
        is EquationResult.Equation ->
            call.respond(HttpStatusCode.OK, EquationResponse(latex = result.latex, imageUrl = result.imageUrl))
        is EquationResult.NotFound ->
            call.respond(HttpStatusCode.UnprocessableEntity, EquationResponse(error = NO_EQUATION))
        is EquationResult.InvalidLatex ->
            call.respond(
                HttpStatusCode.BadRequest,
                EquationResponse(latex = result.latex, error = result.message)
            )
        is EquationResult.Failed ->
            call.respond(HttpStatusCode.ServiceUnavailable, EquationResponse(error = result.message))
    }
}

internal const val NO_EQUATION = "No equation was found in the drawing"
