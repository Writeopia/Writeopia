package io.writeopia.api.export.service

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import java.util.concurrent.TimeUnit

object ExportStorageService {
    private val storage by lazy {
        StorageOptions.getDefaultInstance().service
    }

    private const val SIGNED_URL_EXPIRATION_DAYS = 7L

    fun uploadZip(
        bucketName: String,
        objectPath: String,
        zipBytes: ByteArray
    ): String {
        val blobInfo = BlobInfo.newBuilder(bucketName, objectPath)
            .setContentType("application/zip")
            .build()

        storage.create(blobInfo, zipBytes)

        val blobId = BlobId.of(bucketName, objectPath)
        val signedUrl = storage.signUrl(
            BlobInfo.newBuilder(blobId).build(),
            SIGNED_URL_EXPIRATION_DAYS,
            TimeUnit.DAYS
        )

        return signedUrl.toString()
    }
}
