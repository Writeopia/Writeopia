package io.writeopia.backend.models

import io.ktor.http.content.*

interface ImageStorageService {
    suspend fun uploadImage(multipart: MultiPartData, userId: String): String?
}
