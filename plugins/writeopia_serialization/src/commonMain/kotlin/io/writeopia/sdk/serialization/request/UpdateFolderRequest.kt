package io.writeopia.sdk.serialization.request

import io.writeopia.sdk.serialization.data.IconApi
import kotlinx.serialization.Serializable

@Serializable
data class UpdateFolderRequest(
    val title: String? = null,
    val icon: IconApi? = null,
    val favorite: Boolean? = null,
)
