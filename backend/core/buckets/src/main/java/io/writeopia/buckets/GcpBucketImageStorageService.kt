package io.writeopia.buckets

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import io.ktor.http.content.*
import io.writeopia.backend.models.ImageStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object GcpBucketImageStorageService : ImageStorageService {

    private val storage = GcpStorageProvider.storage

    // Signed URL expiration time (7 days)
    private const val SIGNED_URL_EXPIRATION_DAYS = 7L

    /**
     * Processes multipart data to find an image and upload it to GCP.
     * @return A signed URL for the uploaded image (valid for 7 days), or null if no image was found.
     */
    override suspend fun uploadImage(
        multipart: MultiPartData,
        userId: String,
        debugMode: Boolean
    ): String? =
        withContext(Dispatchers.IO) {
            var uploadedUrl: String? = null
            val bucketName = BucketConfig.imagesBucketName(debugMode)

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName =
                        "uploads/$userId/${System.currentTimeMillis()}-${part.originalFileName}"

                    val blobInfo =
                        BlobInfo.newBuilder(bucketName, fileName)
                            .setContentType(part.contentType?.toString())
                            .build()

                    val bytes = part.streamProvider().readBytes()
                    storage.create(blobInfo, bytes)

                    // Generate a signed URL for private access
                    val blobId = BlobId.of(bucketName, fileName)
                    val signedUrl = storage.signUrl(
                        BlobInfo.newBuilder(blobId).build(),
                        SIGNED_URL_EXPIRATION_DAYS,
                        TimeUnit.DAYS
                    )

                    uploadedUrl = signedUrl.toString()
                }
                part.dispose()
            }

            uploadedUrl
        }

    /**
     * Generates a new signed URL for an existing image.
     * Useful for refreshing expired URLs.
     */
    suspend fun refreshSignedUrl(
        bucketName: String,
        objectPath: String,
        expirationDays: Long = SIGNED_URL_EXPIRATION_DAYS
    ): String = withContext(Dispatchers.IO) {
        val blobId = BlobId.of(bucketName, objectPath)
        val signedUrl = storage.signUrl(
            BlobInfo.newBuilder(blobId).build(),
            expirationDays,
            TimeUnit.DAYS
        )
        signedUrl.toString()
    }
}
