package io.writeopia.sdk.serialization.data.auth

import io.writeopia.sdk.models.Workspace
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val token: String?,
    val writeopiaUser: WriteopiaUserApi,
    val workspace: WorkspaceApi
)
