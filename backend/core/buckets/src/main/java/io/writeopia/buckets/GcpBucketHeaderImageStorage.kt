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
            isHeaderImageUrl(url, BucketConfig.headerImagesBaseUrl(debugMode), workspaceId)
        }

    /**
     * Whether [url] names one picture [store] wrote for [workspaceId]: the CDN host, the folder of
     * the workspace, then a single object name as [store] makes it (`<uuid>.<extension>`). Anything
     * else, like `..` segments, encoded separators, a query or a deeper path, is refused, so a
     * workspace keeps only its own pictures.
     */
    fun isHeaderImageUrl(url: String, baseUrl: String, workspaceId: String): Boolean {
        val prefix = "${baseUrl.trimEnd('/')}/${prefixOf(workspaceId)}"
        if (!url.startsWith(prefix)) return false

        return OBJECT_NAME.matches(url.removePrefix(prefix))
    }

    private val OBJECT_NAME = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(png|jpg|webp)")

    private fun prefixOf(workspaceId: String) = "$FOLDER/$workspaceId/"

    private fun extensionOf(mimeType: String): String =
        when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
}
