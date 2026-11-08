package io.writeopia.storage

import io.writeopia.backend.models.ImageFile
import io.writeopia.backend.models.ImageStorageService
import io.writeopia.connection.logger

/** What storing a picture within the quota came to. */
sealed class MediaStoreResult {
    /** The picture is in the bucket at [url]; [usage] is the user's usage after it, when tracked. */
    data class Stored(val url: String, val usage: StorageUsage?) : MediaStoreResult()

    /** The picture was not stored: it doesn't fit in the quota. */
    data class QuotaExceeded(val usage: StorageUsage, val requestedBytes: Long) : MediaStoreResult()

    /** The picture was not stored: the usage row kept changing under us. Worth a retry. */
    data object Busy : MediaStoreResult()

    /** The bucket refused the picture. The usage is back to what it was. */
    data object Failed : MediaStoreResult()
}

/** What deleting a picture came to. */
sealed class MediaDeleteResult {
    /** The object is gone (or was already); [freedBytes] were released, 0 when nothing was there. */
    data class Deleted(val freedBytes: Long, val usage: StorageUsage?) : MediaDeleteResult()

    /** The URL is not one of the user's own pictures, so nothing was touched. */
    data object NotOwned : MediaDeleteResult()
}

/**
 * The bucket and the usage table together: a picture is counted before it is written, so a
 * user can never go over the quota, and the count is given back when the write fails or the
 * picture is deleted. With no [usage] (no database) the bucket alone is used and nothing is
 * counted.
 */
class MediaStore(
    private val images: ImageStorageService,
    private val usage: StorageUsageService?,
    private val debugMode: Boolean
) {

    suspend fun store(image: ImageFile, userId: String): MediaStoreResult {
        val size = image.bytes.size.toLong()

        val reserved = when (val change = usage?.reserve(userId, size)) {
            null -> null
            is StorageChange.Applied -> change.usage
            is StorageChange.QuotaExceeded -> {
                logger.info(
                    "Upload refused - user {} has {} of {} bytes, asked {} more",
                    userId, change.usage.usedBytes, change.usage.quotaBytes, size
                )
                return MediaStoreResult.QuotaExceeded(change.usage, size)
            }
            is StorageChange.Conflict -> return MediaStoreResult.Busy
        }

        val url = try {
            images.uploadImageBytes(image.bytes, image.contentType, image.fileName, userId, debugMode)
        } catch (e: Exception) {
            logger.error("Failed to store a picture of user {}", userId, e)
            null
        }

        if (url == null) {
            // The bytes never reached the bucket, so they don't count.
            usage?.release(userId, size)
            return MediaStoreResult.Failed
        }

        return MediaStoreResult.Stored(url, reserved)
    }

    /** Deletes the picture at [url] when it belongs to [userId] and gives its bytes back. */
    suspend fun delete(url: String, userId: String): MediaDeleteResult {
        val freed = images.deleteImage(url, userId, debugMode) ?: return MediaDeleteResult.NotOwned

        val after = if (freed > 0) {
            (usage?.release(userId, freed) as? StorageChange.Applied)?.usage
        } else {
            usage?.usage(userId)
        }

        return MediaDeleteResult.Deleted(freed, after)
    }
}
