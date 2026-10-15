package io.writeopia.buckets

import com.google.cloud.storage.BlobInfo
import io.writeopia.backend.models.HeaderImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The header pictures in a public bucket behind Cloud CDN. The objects are named
 * `header-images/$workspaceId/<random id>.<extension>`, and the load balancer serves that path
 * from the bucket, so the URL is the CDN host followed by the object name.
 */
object GcpBucketHeaderImageStorage : HeaderImageStorage {

    private const val FOLDER = "header-images"

    /** The picture of every header in debug mode, where there is no bucket. */
    const val DEBUG_IMAGE_URL = "https://picsum.photos/seed/writeopia-header/1600/900"

    private val storage by lazy { GcpStorageProvider.storage }

    // The name never changes once written, so the CDN and the browsers can keep it for a year.
    private const val CACHE_CONTROL = "public, max-age=31536000, immutable"

    override suspend fun store(
        bytes: ByteArray,
        contentType: String,
        workspaceId: String,
        debugMode: Boolean
    ): String? = withContext(Dispatchers.IO) {
        if (debugMode) return@withContext "$DEBUG_IMAGE_URL&id=${UUID.randomUUID()}"

        val objectName = "${prefixOf(workspaceId)}${UUID.randomUUID()}.${extensionOf(contentType)}"
        val blobInfo = BlobInfo.newBuilder(BucketConfig.headerImagesBucketName(debugMode), objectName)
            .setContentType(contentType)
            .setCacheControl(CACHE_CONTROL)
            .build()

        storage.create(blobInfo, bytes)

        "${BucketConfig.headerImagesBaseUrl(debugMode).trimEnd('/')}/$objectName"
    }

    override fun isHeaderOf(url: String, workspaceId: String, debugMode: Boolean): Boolean =
        if (debugMode) {
            url.startsWith(DEBUG_IMAGE_URL)
        } else {
            url.startsWith("${BucketConfig.headerImagesBaseUrl(debugMode).trimEnd('/')}/${prefixOf(workspaceId)}")
        }

    private fun prefixOf(workspaceId: String) = "$FOLDER/$workspaceId/"

    private fun extensionOf(mimeType: String): String =
        when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
}
