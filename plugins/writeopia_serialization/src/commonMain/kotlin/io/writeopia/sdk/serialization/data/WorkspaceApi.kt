@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.data

import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class WorkspaceApi(
    val id: String,
    val userId: String,
    val name: String,
    val role: String,
    val documentCount: Int = 0,
)

/**
 * Converts WorkspaceApi to Workspace model.
 *
 * @param lastSync The last sync timestamp. Defaults to DISTANT_PAST so new workspaces
 *                 will fetch all their data on first sync. Should only be updated with
 *                 server-provided timestamps after successful syncs.
 */
fun WorkspaceApi.toModel(lastSync: Instant = Instant.DISTANT_PAST) =
    Workspace(
        this.id,
        this.userId,
        this.name,
        lastSync = lastSync,
        selected = false,
        role = role,
        documentCount = documentCount,
    )

fun Workspace.toApi(documentCount: Int = this.documentCount) =
    WorkspaceApi(
        id = this.id,
        userId = this.userId,
        name = this.name,
        role = role,
        documentCount = documentCount,
    )
