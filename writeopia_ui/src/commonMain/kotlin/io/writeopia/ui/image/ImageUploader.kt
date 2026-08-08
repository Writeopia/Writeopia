package io.writeopia.ui.image

import io.writeopia.sdk.models.utils.ResultData

/**
 * Interface for uploading images to a remote storage.
 * Implementations can upload to cloud storage (e.g., GCS) or other backends.
 */
interface ImageUploader {
    /**
     * Uploads an image from the local file system to remote storage.
     * @param localPath The local file path of the image to upload.
     * @return ResultData containing the cloud URL on success, or an error.
     */
    suspend fun uploadImage(localPath: String): ResultData<String>

    /**
     * Checks if the user is authenticated and can upload images.
     * @return true if authenticated, false otherwise.
     */
    suspend fun isAuthenticated(): Boolean
}

/**
 * A no-op implementation of ImageUploader that always fails.
 * Used when image upload functionality is not available.
 */
class NoOpImageUploader : ImageUploader {
    override suspend fun uploadImage(localPath: String): ResultData<String> =
        ResultData.Error()

    override suspend fun isAuthenticated(): Boolean = false
}
