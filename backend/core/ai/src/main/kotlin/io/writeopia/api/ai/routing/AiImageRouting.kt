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
import io.writeopia.api.ai.config.AiConfig
import io.writeopia.api.ai.model.AiRequestResult
import io.writeopia.api.ai.service.AiService
import io.writeopia.api.ai.service.ImagePrompt
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.app.dto.ai.AiImageRequest
import io.writeopia.app.dto.ai.AiImageResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.backend.models.ImageStorageService
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

private const val ENDPOINT_NAME = "image"

/**
 * Draws a picture for a paragraph with the cloud AI and stores it with the user's media. The
 * picture counts in the monthly quota with the tokens the model reports; when it reports none,
 * [AiConfig.imageTokenCost] tokens are charged instead, so every picture costs something.
 */
fun Routing.aiImageRoute(
    genAiService: GenAiService,
    imageStorageService: ImageStorageService,
    debugMode: Boolean,
    writeopiaDb: WriteopiaDbBackend?
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiImage()}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: if (debugMode) "debug-user" else null

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, AiImageResponse(error = "User not authenticated"))
                return@post
            }

            if (!debugMode && writeopiaDb != null) {
                when (val denied = AiService.checkAuthorization(userId, ENDPOINT_NAME, writeopiaDb)) {
                    null -> Unit
                    is AiRequestResult.Forbidden -> {
                        call.respond(HttpStatusCode.Forbidden, AiImageResponse(error = denied.message))
                        return@post
                    }
                    is AiRequestResult.QuotaExceeded -> {
                        call.respond(HttpStatusCode.TooManyRequests, AiImageResponse(error = denied.message))
                        return@post
                    }
                    else -> {
                        call.respond(HttpStatusCode.Forbidden, AiImageResponse(error = "Not allowed"))
                        return@post
                    }
                }
            }

            val request = try {
                call.receive<AiImageRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in AI image endpoint: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, AiImageResponse(error = "Invalid request format"))
                return@post
            }

            if (request.paragraph.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, AiImageResponse(error = "The paragraph cannot be empty"))
                return@post
            }

            logger.info(
                "AI image request - user: {}, paragraph: {} chars, document: {} chars",
                userId, request.paragraph.length, request.documentText.length
            )

            val started = System.currentTimeMillis()
            val generation = genAiService.generateImageWithUsage(ImagePrompt.forParagraph(request))
            val usage = generation.tokenUsage.orImageCost()

            logger.info(
                "AI image answer - user: {}, picture: {}, tokens: {} (reported {}), {} ms, error: {}",
                userId, generation.image != null, usage.totalTokens, generation.tokenUsage.totalTokens,
                System.currentTimeMillis() - started, generation.error
            )

            val image = generation.image
            if (image == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    AiImageResponse(error = generation.error ?: "The AI drew no picture")
                )
                return@post
            }

            // The request was answered, so it costs tokens even when storing the picture fails.
            AiService.saveUsage(
                userId = userId,
                endpointName = ENDPOINT_NAME,
                modelName = "image",
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                writeopiaDb = writeopiaDb
            )

            val imageUrl = try {
                imageStorageService.uploadImageBytes(
                    bytes = image.bytes,
                    contentType = image.mimeType,
                    fileName = "ai-image.${extensionOf(image.mimeType)}",
                    userId = userId,
                    debugMode = debugMode
                )
            } catch (e: Exception) {
                logger.error("Failed to store the AI image for user {}", userId, e)
                null
            }

            if (imageUrl == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    AiImageResponse(error = "The picture could not be stored")
                )
                return@post
            }

            call.respond(HttpStatusCode.OK, AiImageResponse(imageUrl = imageUrl))
        }
    }
}

/** The reported tokens, or the fixed cost of a picture when the model reported none. */
internal fun TokenUsage.orImageCost(cost: Int = AiConfig.imageTokenCost()): TokenUsage =
    if (totalTokens > 0) this else TokenUsage(inputTokens = 0, outputTokens = cost, totalTokens = cost)

private fun extensionOf(mimeType: String): String =
    when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }
