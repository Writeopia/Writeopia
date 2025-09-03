package io.writeopia.api.core.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class AddUserToWorkspaceRequest(val email: String, val workspaceId: String)
