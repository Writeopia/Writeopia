package io.writeopia.sdk.serialization.data

import io.writeopia.sdk.models.Workspace
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceApi(
    val id: String,
    val userId: String,
    val name: String,
)

fun WorkspaceApi.toModel() =
    Workspace(this.id, this.userId, this.name, lastSync = Clock.System.now(), selected = false)

fun Workspace.toApi() = WorkspaceApi(this.id, this.userId, this.name)
