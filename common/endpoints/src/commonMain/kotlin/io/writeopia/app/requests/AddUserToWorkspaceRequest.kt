package io.writeopia.app.requests

import kotlinx.serialization.Serializable

@Serializable
data class AddUserToWorkspaceRequest(val username: String, val workspaceId: String, val role: String)
