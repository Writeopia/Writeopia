package io.writeopia.sdk.serialization.data

import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
data class WorkspaceApi(
    val id: String,
    val userId: String,
    val name: String,
    val role: String,
)

fun WorkspaceApi.toModel(lastSync: Instant = Clock.System.now()) =
    Workspace(
        this.id,
        this.userId,
        this.name,
        lastSync = lastSync,
        selected = false,
        role = role
    )

fun Workspace.toApi() =
    WorkspaceApi(id = this.id, userId = this.userId, name = this.name, role = role)
