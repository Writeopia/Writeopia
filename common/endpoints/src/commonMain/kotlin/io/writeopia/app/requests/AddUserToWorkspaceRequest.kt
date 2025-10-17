package io.writeopia.app.requests

import kotlinx.serialization.Serializable

@Serializable
data class AddUserToWorkspaceRequest(val email: String, val workspaceId: String, val role: String)
