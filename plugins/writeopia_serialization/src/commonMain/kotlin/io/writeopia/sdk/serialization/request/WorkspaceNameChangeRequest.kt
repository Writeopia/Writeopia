package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceNameChangeRequest(val workspaceId: String, val newName: String)
