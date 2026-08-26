package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class EventDiffRequest(
    val workspaceId: String,
    val lastEventSync: Long
)
