package io.writeopia.core.folders.sync

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
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.request.ImageUploadRequest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

class ImageSync(
    private val client: HttpClient,
    private val baseUrl: String,
    private val documentRepository: DocumentRepository
) {

    suspend fun syncAllImages(workspaceId: String, token: String) {
        val storySteps = documentRepository.queryUnsyncedImagesSteps()

        storySteps
            .filter { storyStep -> storyStep.path != null }
            .forEach { step ->
                val result = sendImageFromPath(
                    imagePath = step.path!!,
                    workspaceId = workspaceId,
                    token = token
                )

                if (result is ResultData.Complete) {
                    val url = step.url

                    if (url != null) {
                        documentRepository.updateStoryStepUrl(url, step.id)
                    }
                }
            }
    }

    private suspend fun sendImageFromPath(
        imagePath: String,
        workspaceId: String,
        token: String
    ): ResultData<ImageUploadRequest> =
        try {
            val contentType = detectContentType(imagePath)

            val response: HttpResponse = client.submitFormWithBinaryData(
                url = "$baseUrl/api/workspace/$workspaceId/document/upload-image",
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
                ResultData.Complete(response.body<ImageUploadRequest>())
            } else {
                ResultData.Error()
            }
        } catch (e: Exception) {
            ResultData.Error(e)
        }
}

private fun detectContentType(path: String): ContentType {
    val extension = path.substringAfterLast(".", "").lowercase()
    return when (extension) {
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "png" -> ContentType.Image.PNG
        "gif" -> ContentType.Image.GIF
        "webp" -> ContentType.Image.Any
        "svg" -> ContentType.Image.SVG
        else -> ContentType.Application.OctetStream // Fallback for unknown types
    }
}
