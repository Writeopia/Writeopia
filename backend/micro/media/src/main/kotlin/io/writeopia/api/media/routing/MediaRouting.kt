package io.writeopia.api.media.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.writeopia.backend.models.ImageStorageService
import io.writeopia.buckets.GcpBucketImageStorageService
import io.writeopia.connection.logger
import kotlinx.serialization.Serializable

@Serializable
data class ImageUploadResponse(val imageUrl: String)

fun Routing.mediaRoute(
    debug: Boolean = false,
    imageStorageService: ImageStorageService = GcpBucketImageStorageService
) {
    authenticate("auth-jwt", optional = debug) {
        post("/api/media/upload") {
            val userId = getUserId()

            if (userId == null && !debug) {
                call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                return@post
            }

            val effectiveUserId = userId ?: "debug-user"
            val multipart = call.receiveMultipart()

            val imageUrl = imageStorageService.uploadImage(multipart, effectiveUserId, debug)

            if (imageUrl != null) {
                call.respond(HttpStatusCode.Created, ImageUploadResponse(imageUrl))
            } else {
                call.respond(HttpStatusCode.BadRequest, "No image found in request")
            }
        }
    }
}

private fun RoutingContext.getUserId(): String? {
    val principal = call.principal<JWTPrincipal>()

    if (principal == null) {
        logger.warn("principal is null")
    }

    return principal?.payload?.getClaim("userId")?.asString()
}
