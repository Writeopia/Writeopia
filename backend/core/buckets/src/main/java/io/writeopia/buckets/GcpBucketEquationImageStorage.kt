package io.writeopia.buckets

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import io.writeopia.backend.models.EquationImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * The pictures of the equations, in the public bucket of the header pictures behind Cloud CDN.
 * The objects are named by the caller after the LaTeX they draw (`equations/<hash>.png`), so an
 * equation is stored once and the name never changes: the CDN and the browsers keep it for a year.
 */
object GcpBucketEquationImageStorage : EquationImageStorage {

    private val storage by lazy { GcpStorageProvider.storage }

    private const val CACHE_CONTROL = "public, max-age=31536000, immutable"

    override suspend fun store(
        bytes: ByteArray,
        contentType: String,
        objectName: String,
        debugMode: Boolean
    ): String = withContext(Dispatchers.IO) {
        // No bucket in debug mode: the picture travels inside the URL.
        if (debugMode) return@withContext "data:$contentType;base64,${Base64.getEncoder().encodeToString(bytes)}"

        val bucket = BucketConfig.headerImagesBucketName(debugMode)
        if (storage.get(BlobId.of(bucket, objectName)) == null) {
            val blobInfo = BlobInfo.newBuilder(bucket, objectName)
                .setContentType(contentType)
                .setCacheControl(CACHE_CONTROL)
                .build()
            storage.create(blobInfo, bytes)
        }

        "${BucketConfig.headerImagesBaseUrl(debugMode).trimEnd('/')}/$objectName"
    }
}
