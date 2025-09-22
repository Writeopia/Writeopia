package io.writeopia.api.core.auth.dto

import io.writeopia.sdk.serialization.data.WorkspaceApi
import kotlinx.serialization.Serializable

@Serializable
data class WorkspacesResponse(val workspaces: List<WorkspaceApi>)
