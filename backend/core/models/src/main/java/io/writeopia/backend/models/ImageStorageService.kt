package io.writeopia.backend.models

import io.ktor.http.content.MultiPartData

interface ImageStorageService {
    suspend fun uploadImage(multipart: MultiPartData, userId: String, debugMode: Boolean): String?

    /**
     * Stores an image the backend already holds, like one the AI drew, under the folder of
     * [userId]. [fileName] is the name inside that folder, with its extension.
     * @return The URL of the stored image, or null when it couldn't be stored.
     */
    suspend fun uploadImageBytes(
        bytes: ByteArray,
        contentType: String,
        fileName: String,
        userId: String,
        debugMode: Boolean
    ): String?
}
