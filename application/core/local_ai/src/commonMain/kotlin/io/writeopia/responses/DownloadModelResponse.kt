package io.writeopia.responses

import kotlinx.serialization.Serializable

@Serializable
data class DownloadModelResponse(
    val status: String? = null,
    val digest: String? = null,
    val total: Long? = null,
    val completed: Long? = null,
    val modelName: String? = null,
    val error: String? = null
)
