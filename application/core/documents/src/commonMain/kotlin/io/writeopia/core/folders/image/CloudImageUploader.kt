package io.writeopia.core.folders.image

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.MediaApi
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.image.ImageUploader

/**
 * Implementation of ImageUploader that uploads images to cloud storage
 * via the media microservice.
 */
class CloudImageUploader(
    private val mediaApi: MediaApi,
    private val authRepository: AuthRepository
) : ImageUploader {

    override suspend fun uploadImage(localPath: String): ResultData<String> =
        mediaApi.uploadImage(localPath)

    override suspend fun isAuthenticated(): Boolean =
        authRepository.getAuthToken() != null
}
