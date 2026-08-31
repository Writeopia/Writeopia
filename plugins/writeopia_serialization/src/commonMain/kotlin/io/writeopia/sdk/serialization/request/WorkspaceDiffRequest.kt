package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceDiffRequest(
    val workspaceId: String,
    val lastSync: Long,
    val orderBy: String = "last_updated_at"
)
