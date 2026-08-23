package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

@Serializable
data class EventDiffResponse(
    val events: List<SyncEventApi>,
    val serverTimestamp: Long
)

@Serializable
data class SyncEventApi(
    val id: String,
    val eventType: String,
    val entityId: String,
    val oldParentId: String? = null,
    val newParentId: String? = null,
    val createdAt: Long
)
