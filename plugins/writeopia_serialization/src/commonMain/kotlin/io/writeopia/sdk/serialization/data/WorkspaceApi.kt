@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.data

import io.writeopia.sdk.models.workspace.Workspace
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
