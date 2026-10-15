package io.writeopia.buckets

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

object GcpStorageProvider {
    val storage: Storage by lazy {
        StorageOptions.getDefaultInstance().service
    }
}

object BucketConfig {

    fun imagesBucketName(debugMode: Boolean): String =
        if (debugMode) {
            ""
        } else {
            System.getenv("IMAGES_BUCKET_NAME")
                ?: throw IllegalStateException(
                    "Environment variable 'IMAGES_BUCKET_NAME' is not set."
                )
        }

    fun bucketBaseUrl(debugMode: Boolean): String =
        if (debugMode) {
            ""
        } else {
            System.getenv("BUCKET_BASE_NAME")
                ?: throw IllegalStateException(
                    "Environment variable 'BUCKET_BASE_NAME' is not set."
                )
        }

    /** The public bucket of the header pictures, behind the CDN. */
    fun headerImagesBucketName(debugMode: Boolean): String =
        if (debugMode) {
            ""
        } else {
            System.getenv("HEADER_IMAGES_BUCKET_NAME")
                ?: throw IllegalStateException(
                    "Environment variable 'HEADER_IMAGES_BUCKET_NAME' is not set."
                )
        }

    /** The host the CDN serves the header pictures from, like https://writeopia.io */
    fun headerImagesBaseUrl(debugMode: Boolean): String =
        if (debugMode) {
            ""
        } else {
            System.getenv("HEADER_IMAGES_BASE_URL")
                ?: throw IllegalStateException(
                    "Environment variable 'HEADER_IMAGES_BASE_URL' is not set."
                )
        }
}
