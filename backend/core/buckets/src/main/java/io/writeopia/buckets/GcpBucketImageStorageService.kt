package io.writeopia.buckets

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import io.writeopia.backend.models.ImageStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit

object GcpBucketImageStorageService : ImageStorageService {

    private val storage by lazy { GcpStorageProvider.storage } // Don't run when it's not accessed useful in debug mode

    // Signed URL expiration time (7 days)
    private const val SIGNED_URL_EXPIRATION_DAYS = 7L

    private const val UPLOADS_FOLDER = "uploads"

    /** The host of the path-style URLs `Storage.signUrl` makes: `https://storage.googleapis.com/<bucket>/<object>`. */
    private const val STORAGE_HOST = "storage.googleapis.com"

    /**
     * Stores [bytes] under the uploads folder of [userId]: a file the user sent or a picture
     * the AI drew. Everything of a user lives under `uploads/$userId/`, so the account deletion
     * finds it with the rest of the user's media and [deleteImage] knows what is theirs.
     * @return A signed URL (valid for 7 days), or a placeholder in debug mode.
     */
    override suspend fun uploadImageBytes(
        bytes: ByteArray,
        contentType: String,
        fileName: String,
        userId: String,
        debugMode: Boolean
    ): String? = withContext(Dispatchers.IO) {
        if (debugMode) {
            "https://picsum.photos/200"
        } else {
            store(
                bucketName = BucketConfig.imagesBucketName(debugMode),
                fileName = "${prefixOf(userId)}${System.currentTimeMillis()}-$fileName",
                contentType = contentType,
                bytes = bytes
            )
        }
    }

    /**
     * Deletes the object [url] points to when it is in the folder of [userId]. Reads the size
     * first, so the caller can give the bytes back to the user's storage.
     */
    override suspend fun deleteImage(url: String, userId: String, debugMode: Boolean): Long? =
        withContext(Dispatchers.IO) {
            if (debugMode) return@withContext 0L

            val bucketName = BucketConfig.imagesBucketName(debugMode)
            val objectName = objectNameOf(url, bucketName, userId) ?: return@withContext null

            val blob = storage.get(BlobId.of(bucketName, objectName)) ?: return@withContext 0L
            val size = blob.size ?: 0L
            blob.delete()
            size
        }

    /** Writes the object and signs a URL for it. */
    private fun store(bucketName: String, fileName: String, contentType: String?, bytes: ByteArray): String {
        val blobInfo = BlobInfo.newBuilder(bucketName, fileName)
            .setContentType(contentType)
            .build()

        storage.create(blobInfo, bytes)

        val blobId = BlobId.of(bucketName, fileName)
        val signedUrl = storage.signUrl(
            BlobInfo.newBuilder(blobId).build(),
            SIGNED_URL_EXPIRATION_DAYS,
            TimeUnit.DAYS
        )

        return signedUrl.toString()
    }

    /**
     * Deletes every object under [prefix] in [bucketName] (e.g. "uploads/$userId/" - see
     * uploadImageBytes's fileName convention). Used by the account-deletion saga: media is stored
     * per-user, not per-workspace, so a single prefix delete covers everything regardless of
     * how many workspaces the user was in. Idempotent - a retry after a partial failure just
     * finds fewer (or no) objects left to delete.
     */
    suspend fun deleteAllUnderPrefix(bucketName: String, prefix: String): Unit =
        withContext(Dispatchers.IO) {
            storage.list(bucketName, Storage.BlobListOption.prefix(prefix))
                .iterateAll()
                .forEach { blob -> blob.delete() }
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

    /**
     * The object name [url] points to, when it is a URL of [bucketName] (path-style, as
     * [store] signs them, or virtual-hosted `<bucket>.storage.googleapis.com`) and the object is
     * directly in the folder of [userId]. The query (the signature) is ignored. Anything else,
     * like another user's folder, a `..` segment or a deeper path, gives null, so a user can
     * only delete their own pictures.
     */
    fun objectNameOf(url: String, bucketName: String, userId: String): String? {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }

        if (uri.scheme != "https" || bucketName.isEmpty() || userId.isEmpty()) return null

        // getPath decodes the percent-encoding of the signed URL (spaces, unicode in file names).
        val path = uri.path?.removePrefix("/") ?: return null
        val objectName = when (uri.host) {
            STORAGE_HOST -> path.removePrefix("$bucketName/").takeIf { it != path }
            "$bucketName.$STORAGE_HOST" -> path
            else -> null
        } ?: return null

        val prefix = prefixOf(userId)
        if (!objectName.startsWith(prefix)) return null

        val name = objectName.removePrefix(prefix)
        if (name.isEmpty() || name.contains('/') || name == "." || name == "..") return null

        return objectName
    }

    private fun prefixOf(userId: String) = "$UPLOADS_FOLDER/$userId/"
}
