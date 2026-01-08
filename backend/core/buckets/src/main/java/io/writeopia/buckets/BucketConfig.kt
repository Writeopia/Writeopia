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
}
