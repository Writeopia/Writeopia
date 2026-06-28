package io.writeopia.app.requests

import kotlinx.serialization.Serializable

@Serializable
data class CreateWorkspaceRequest(val name: String)
