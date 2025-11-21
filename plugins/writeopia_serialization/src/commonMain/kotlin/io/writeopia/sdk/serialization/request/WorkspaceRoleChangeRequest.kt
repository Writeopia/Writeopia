package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceRoleChangeRequest(
    val workspaceId: String,
    val userId: String,
    val newRole: String
)
