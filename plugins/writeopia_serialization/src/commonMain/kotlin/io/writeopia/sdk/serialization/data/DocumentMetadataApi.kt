package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class DocumentMetadataApi(
    val title: String? = null,
    val icon: String? = null,
    val iconTint: Int? = null,
    val favorite: Boolean? = null,
    val lastUpdatedAt: Long
)
