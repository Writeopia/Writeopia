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
     * Deletes an image this uploader stored, when the user removes it from the document, so
     * the remote storage can give its space back. By default nothing is deleted.
     * @param url The URL [uploadImage] answered with.
     */
    suspend fun deleteImage(url: String): ResultData<Unit> = ResultData.Complete(Unit)

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
