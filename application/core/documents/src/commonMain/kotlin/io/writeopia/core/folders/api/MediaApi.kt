package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.request.MediaDeleteRequest
import io.writeopia.sdk.serialization.response.StorageUsageResponse
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

@Serializable
data class MediaUploadResponse(val imageUrl: String)

/** The backend refused a picture: the user's storage is full. [usage] says how full. */
class StorageQuotaExceededException(val usage: StorageUsageResponse?) :
    Exception("The storage is full")

/**
 * API client for the media microservice.
 * Handles image uploads to cloud storage.
 * Authentication is handled automatically by the HTTP client's bearer auth plugin.
 */
class MediaApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    /**
     * Uploads an image to the media service.
     * @param imagePath Local file path of the image to upload.
     * @return ResultData containing the cloud URL on success, or an error: a
     * [StorageQuotaExceededException] when the user's storage is full.
     */
    suspend fun uploadImage(imagePath: String): ResultData<String> =
        try {
            val contentType = detectContentType(imagePath)

            val response: HttpResponse = client.submitFormWithBinaryData(
                url = "$baseUrl/${EndPoints.mediaUpload()}",
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
                        }
                    )
                }
            )

            when (response.status) {
                HttpStatusCode.Created -> {
                    val mediaResponse = response.body<MediaUploadResponse>()
                    ResultData.Complete(mediaResponse.imageUrl)
                }

                HttpStatusCode.InsufficientStorage -> {
                    val usage = try {
                        response.body<StorageUsageResponse>()
                    } catch (e: Exception) {
                        null
                    }
                    ResultData.Error(StorageQuotaExceededException(usage))
                }

                else -> {
                    println("Media upload failed with status: ${response.status}")
                    ResultData.Error()
                }
            }
        } catch (e: Exception) {
            println("Media upload error: ${e.message}")
            ResultData.Error(e)
        }

    /** How much media the user keeps on the backend, and the quota. */
    suspend fun getUsage(): ResultData<StorageUsageResponse> =
        try {
            val response = client.get("$baseUrl/${EndPoints.mediaUsage()}")

            if (response.status == HttpStatusCode.OK) {
                ResultData.Complete(response.body<StorageUsageResponse>())
            } else {
                ResultData.Error(Exception("Storage usage failed with status: ${response.status}"))
            }
        } catch (e: Exception) {
            ResultData.Error(e)
        }

    /**
     * Deletes one of the user's pictures on the backend, giving its bytes back to the storage.
     * @return The usage after the deletion when the backend reports it.
     */
    suspend fun deleteImage(imageUrl: String): ResultData<StorageUsageResponse?> =
        try {
            val response = client.post("$baseUrl/${EndPoints.mediaDelete()}") {
                contentType(ContentType.Application.Json)
                setBody(MediaDeleteRequest(imageUrl))
            }

            if (response.status == HttpStatusCode.OK) {
                val usage = try {
                    response.body<StorageUsageResponse>()
                } catch (e: Exception) {
                    null
                }
                ResultData.Complete(usage)
            } else {
                ResultData.Error(Exception("Media delete failed with status: ${response.status}"))
            }
        } catch (e: Exception) {
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
