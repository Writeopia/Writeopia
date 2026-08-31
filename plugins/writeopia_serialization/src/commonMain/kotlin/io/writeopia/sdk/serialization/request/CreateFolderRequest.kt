package io.writeopia.sdk.serialization.request

import io.writeopia.sdk.serialization.data.IconApi
import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderRequest(
    val title: String,
    val icon: IconApi? = null
)
