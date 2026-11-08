package io.writeopia.backend.models

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider

/** A picture as it arrived in a request: its bytes, type and the name it was sent with. */
data class ImageFile(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String
)

/**
 * The first file of a multipart request, or null when it carries none. The whole file is read
 * here so the caller can count its bytes before deciding to store it.
 */
suspend fun MultiPartData.readImageFile(): ImageFile? {
    var image: ImageFile? = null

    forEachPart { part ->
        if (part is PartData.FileItem && image == null) {
            image = ImageFile(
                bytes = part.streamProvider().readBytes(),
                contentType = part.contentType?.toString() ?: "application/octet-stream",
                fileName = part.originalFileName ?: "image"
            )
        }
        part.dispose()
    }

    return image
}

interface ImageStorageService {
    /**
     * Stores an image under the folder of [userId]: one the user sent, or one the AI drew.
     * [fileName] is the name inside that folder, with its extension.
     * @return The URL of the stored image, or null when it couldn't be stored.
     */
    suspend fun uploadImageBytes(
        bytes: ByteArray,
        contentType: String,
        fileName: String,
        userId: String,
        debugMode: Boolean
    ): String?

    /**
     * Deletes the image at [url] when it is one of the images of [userId].
     * @return The size of the deleted object in bytes, 0 when there was nothing left to delete,
     * or null when [url] is not a picture of [userId].
     */
    suspend fun deleteImage(url: String, userId: String, debugMode: Boolean): Long?
}
