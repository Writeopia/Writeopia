package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

/** A tag of the workspace catalogue, as sent to and received from the backend. Times are epoch millis. */
@Serializable
data class DocumentTagApi(
    val id: String,
    val workspaceId: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val lastUpdatedAt: Long,
    val deleted: Boolean = false,
)
