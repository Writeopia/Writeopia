package io.writeopia.api.media.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.backend.models.ImageStorageService
import io.writeopia.backend.models.readImageFile
import io.writeopia.buckets.GcpBucketImageStorageService
import io.writeopia.connection.logger
import io.writeopia.sdk.serialization.request.MediaDeleteRequest
import io.writeopia.sdk.serialization.response.StorageUsageResponse
import io.writeopia.storage.MediaDeleteResult
import io.writeopia.storage.MediaStore
import io.writeopia.storage.MediaStoreResult
import io.writeopia.storage.StorageUsage
import io.writeopia.storage.StorageUsageService
import kotlinx.serialization.Serializable

@Serializable
data class ImageUploadResponse(val imageUrl: String)

/**
 * The media of the users: uploads go to the bucket and count in the user's storage, which the
 * user can read and give back by deleting a picture. [storageUsage] is only null in debug
 * mode, where the pictures are stored but not counted.
 */
fun Routing.mediaRoute(
    debug: Boolean = false,
    imageStorageService: ImageStorageService = GcpBucketImageStorageService,
    storageUsage: StorageUsageService? = null
) {
    require(debug || storageUsage != null) { "Uploads must be counted: the storage usage is required" }

    val mediaStore = MediaStore(imageStorageService, storageUsage, debug)

    authenticate("auth-jwt", optional = debug) {
        post("/${EndPoints.mediaUpload()}") {
            val userId = userIdOrDebug(debug) ?: run {
                call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                return@post
            }

            val image = call.receiveMultipart().readImageFile()
            if (image == null) {
                call.respond(HttpStatusCode.BadRequest, "No image found in request")
                return@post
            }

            when (val result = mediaStore.store(image, userId)) {
                is MediaStoreResult.Stored -> call.respond(HttpStatusCode.Created, ImageUploadResponse(result.url))
                is MediaStoreResult.QuotaExceeded -> call.respondQuotaExceeded(result.usage)
                is MediaStoreResult.Busy -> call.respond(HttpStatusCode.ServiceUnavailable, "Storage busy, try again")
                is MediaStoreResult.Failed -> call.respond(HttpStatusCode.ServiceUnavailable, "The image could not be stored")
            }
        }

        get("/${EndPoints.mediaUsage()}") {
            val userId = userIdOrDebug(debug) ?: run {
                call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                return@get
            }

            if (storageUsage == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Storage usage is not available")
                return@get
            }

            call.respond(HttpStatusCode.OK, storageUsage.usage(userId).toApi())
        }

        post("/${EndPoints.mediaDelete()}") {
            val userId = userIdOrDebug(debug) ?: run {
                call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                return@post
            }

            val request = try {
                call.receive<MediaDeleteRequest>()
            } catch (e: ContentTransformationException) {
                logger.warn("Bad request deleting media: {}", e::class.simpleName)
                call.respond(HttpStatusCode.BadRequest, "Invalid request format")
                return@post
            }

            when (val result = mediaStore.delete(request.imageUrl, userId)) {
                is MediaDeleteResult.NotOwned -> call.respond(HttpStatusCode.BadRequest, "Not a picture of this user")
                is MediaDeleteResult.Deleted -> {
                    val usage = result.usage ?: storageUsage?.usage(userId)
                    if (usage != null) {
                        call.respond(HttpStatusCode.OK, usage.toApi())
                    } else {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
    }
}

private fun StorageUsage.toApi() = StorageUsageResponse(usedBytes, quotaBytes, version)

/** 507 Insufficient Storage, with the usage so the client can show how full the space is. */
private suspend fun ApplicationCall.respondQuotaExceeded(usage: StorageUsage) {
    respond(HttpStatusCode.InsufficientStorage, usage.toApi())
}

private fun RoutingContext.userIdOrDebug(debug: Boolean): String? {
    val principal = call.principal<JWTPrincipal>()

    if (principal == null && !debug) {
        logger.warn("principal is null")
    }

    return principal?.payload?.getClaim("userId")?.asString() ?: if (debug) "debug-user" else null
}
