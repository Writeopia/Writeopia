package io.writeopia.app.dto

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceUserApi(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
)
