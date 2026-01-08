package io.writeopia.buckets

import com.google.cloud.storage.BlobInfo
import io.ktor.http.content.*
import io.writeopia.backend.models.ImageStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GcpBucketImageStorageService: ImageStorageService {

    private val storage = GcpStorageProvider.storage
    private val bucketName = BucketConfig.imagesBucketName()
    private val storageBaseUrl = BucketConfig.bucketBaseUrl()

    /**
     * Processes multipart data to find an image and upload it to GCP.
     * @return The public URL of the uploaded image, or null if no image was found.
     */
    override suspend fun uploadImage(multipart: MultiPartData, userId: String): String? =
        withContext(Dispatchers.IO) {
            var uploadedUrl: String? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName =
                        "uploads/$userId/${System.currentTimeMillis()}-${part.originalFileName}"

                    val blobInfo = BlobInfo.newBuilder(bucketName, fileName)
                        .setContentType(part.contentType?.toString())
                        .build()

                    val bytes = part.streamProvider().readBytes()
                    val blob = storage.create(blobInfo, bytes)

                    uploadedUrl = "$storageBaseUrl/$bucketName/${blob.name}"
                }
                part.dispose()
            }

            uploadedUrl
        }
}
