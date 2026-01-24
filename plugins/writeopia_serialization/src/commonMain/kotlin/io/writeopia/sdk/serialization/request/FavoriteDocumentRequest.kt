package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteDocumentRequest(
    val favorite: Boolean
)
