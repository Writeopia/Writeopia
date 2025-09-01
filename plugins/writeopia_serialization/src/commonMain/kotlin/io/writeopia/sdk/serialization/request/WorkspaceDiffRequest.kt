package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceDiffRequest(
    val workspaceId: String,
    val lastSync: Long
)
