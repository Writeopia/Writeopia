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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.ai.model.AiRequestResult
import io.writeopia.api.ai.repository.getHeaderImages
import io.writeopia.api.ai.repository.isMemberOfWorkspace
import io.writeopia.api.ai.repository.saveHeaderImage
import io.writeopia.api.ai.service.AiService
import io.writeopia.api.ai.service.ImagePrompt
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.sdk.serialization.request.HeaderImageGenerateRequest
import io.writeopia.sdk.serialization.response.HeaderImageGenerateResponse
import io.writeopia.sdk.serialization.request.HeaderImageSaveRequest
import io.writeopia.sdk.serialization.response.HeaderImagesResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.backend.models.HeaderImageStorage
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

private const val ENDPOINT_NAME = "header-image"

/**
 * The header pictures of the documents. The cloud AI draws one for a document and the picture
 * goes to the CDN, charged to the user like the other images; the app shows it for the user to
 * approve or draw again. An approved picture joins the pictures of the workspace, which any
 * document of the workspace can then use.
 */
fun Routing.headerImageRoute(
    genAiService: GenAiService,
    headerImageStorage: HeaderImageStorage,
    debugMode: Boolean,
    writeopiaDb: WriteopiaDbBackend?
) {
    authenticate("auth-jwt", optional = debugMode) {
        post("/${EndPoints.aiHeaderImage()}") {
            val userId = userIdOrDebug(debugMode)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, HeaderImageGenerateResponse(error = "User not authenticated"))
                return@post
            }

            val request = try {
                call.receive<HeaderImageGenerateRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request in header image endpoint: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, HeaderImageGenerateResponse(error = "Invalid request format"))
                return@post
            }

            if (!isMember(userId, request.workspaceId, writeopiaDb, debugMode)) {
                call.respond(HttpStatusCode.Forbidden, HeaderImageGenerateResponse(error = "Not a member of the workspace"))
                return@post
            }

            if (!debugMode && writeopiaDb != null) {
                when (val denied = AiService.checkAuthorization(userId, ENDPOINT_NAME, writeopiaDb)) {
                    null -> Unit
                    is AiRequestResult.QuotaExceeded -> {
                        call.respond(HttpStatusCode.TooManyRequests, HeaderImageGenerateResponse(error = denied.message))
                        return@post
                    }
                    is AiRequestResult.Forbidden -> {
                        call.respond(HttpStatusCode.Forbidden, HeaderImageGenerateResponse(error = denied.message))
                        return@post
                    }
                    else -> {
                        call.respond(HttpStatusCode.Forbidden, HeaderImageGenerateResponse(error = "Not allowed"))
                        return@post
                    }
                }
            }

            logger.info(
                "Header image request - user: {}, workspace: {}, document: {} chars",
                userId, request.workspaceId, request.documentText.length
            )

            val started = System.currentTimeMillis()
            val generation = genAiService.generateImageWithUsage(
                prompt = ImagePrompt.forHeader(request),
                aspectRatio = ImagePrompt.HEADER_ASPECT_RATIO
            )
            val usage = generation.tokenUsage.orImageCost()

            logger.info(
                "Header image answer - user: {}, picture: {}, tokens: {} (reported {}), {} ms, error: {}",
                userId, generation.image != null, usage.totalTokens, generation.tokenUsage.totalTokens,
                System.currentTimeMillis() - started, generation.error
            )

            val image = generation.image
            if (image == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HeaderImageGenerateResponse(error = generation.error ?: "The AI drew no picture")
                )
                return@post
            }

            // The picture was drawn, so it costs tokens even when storing it fails.
            AiService.saveUsage(
                userId = userId,
                endpointName = ENDPOINT_NAME,
                modelName = "image",
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                writeopiaDb = writeopiaDb
            )

            val imageUrl = try {
                headerImageStorage.store(
                    bytes = image.bytes,
                    contentType = image.mimeType,
                    workspaceId = request.workspaceId,
                    debugMode = debugMode
                )
            } catch (e: Exception) {
                logger.error("Failed to store the header image for user {}", userId, e)
                null
            }

            if (imageUrl == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HeaderImageGenerateResponse(error = "The picture could not be stored")
                )
                return@post
            }

            call.respond(HttpStatusCode.OK, HeaderImageGenerateResponse(imageUrl = imageUrl))
        }

        get("/${EndPoints.workspaceHeaderImages()}") {
            val userId = userIdOrDebug(debugMode)
            val workspaceId = call.parameters["workspaceId"]

            if (userId == null || workspaceId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            if (!isMember(userId, workspaceId, writeopiaDb, debugMode)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            call.respond(HttpStatusCode.OK, HeaderImagesResponse(writeopiaDb?.getHeaderImages(workspaceId).orEmpty()))
        }

        post("/${EndPoints.workspaceHeaderImages()}") {
            val userId = userIdOrDebug(debugMode)
            val workspaceId = call.parameters["workspaceId"]

            if (userId == null || workspaceId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            if (!isMember(userId, workspaceId, writeopiaDb, debugMode)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val request = try {
                call.receive<HeaderImageSaveRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request saving a header image: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            // Only pictures drawn for this workspace join it, not any address of the web.
            if (!headerImageStorage.isHeaderOf(request.imageUrl, workspaceId, debugMode)) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (writeopiaDb == null) {
                call.respond(HttpStatusCode.ServiceUnavailable)
                return@post
            }

            val image = writeopiaDb.saveHeaderImage(workspaceId, request.imageUrl, userId)
            call.respond(HttpStatusCode.OK, image)
        }
    }
}

private fun RoutingContext.userIdOrDebug(debugMode: Boolean): String? =
    call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?: if (debugMode) "debug-user" else null

private fun isMember(
    userId: String,
    workspaceId: String,
    writeopiaDb: WriteopiaDbBackend?,
    debugMode: Boolean
): Boolean =
    debugMode || writeopiaDb?.isMemberOfWorkspace(userId, workspaceId) == true
