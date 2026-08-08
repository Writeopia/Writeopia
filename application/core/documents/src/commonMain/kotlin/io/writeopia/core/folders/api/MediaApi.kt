package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

@Serializable
data class MediaUploadResponse(val imageUrl: String)

/**
 * API client for the media microservice.
 * Handles image uploads to cloud storage.
 */
class MediaApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    /**
     * Uploads an image to the media service.
     * @param imagePath Local file path of the image to upload.
     * @param token JWT authentication token.
     * @return ResultData containing the cloud URL on success, or an error.
     */
    suspend fun uploadImage(imagePath: String, token: String): ResultData<String> =
        try {
            val contentType = detectContentType(imagePath)

            val response: HttpResponse = client.submitFormWithBinaryData(
                url = "$baseUrl/api/media/upload",
                formData = formData {
                    append(
                        "image",
                        SystemFileSystem.source(Path(imagePath)).buffered().readByteArray(),
                        Headers.build {
                            append(HttpHeaders.ContentType, contentType.toString())
                            append(
                                HttpHeaders.ContentDisposition,
                                "filename=\"${imagePath.substringAfterLast("/")}\""
                            )
                            append(HttpHeaders.Authorization, "Bearer $token")
                        }
                    )
                }
            )

            if (response.status == HttpStatusCode.Created) {
                val mediaResponse = response.body<MediaUploadResponse>()
                ResultData.Complete(mediaResponse.imageUrl)
            } else {
                println("Media upload failed with status: ${response.status}")
                ResultData.Error()
            }
        } catch (e: Exception) {
            println("Media upload error: ${e.message}")
            ResultData.Error(e)
        }

    private fun detectContentType(path: String): ContentType {
        val extension = path.substringAfterLast(".", "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "gif" -> ContentType.Image.GIF
            "webp" -> ContentType.Image.Any
            "svg" -> ContentType.Image.SVG
            else -> ContentType.Application.OctetStream
        }
    }
}
