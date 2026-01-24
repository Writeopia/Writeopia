package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class MoveFolderRequest(
    val targetParentId: String
)
