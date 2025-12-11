package io.writeopia.persistence.room.extensions

import io.writeopia.persistence.room.data.entities.WorkspaceEntity
import io.writeopia.sdk.models.workspace.Workspace
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun WorkspaceEntity.toDomain(): Workspace = Workspace(
    id = id,
    userId = userId,
    name = name,
    lastSync = Instant.fromEpochMilliseconds(lastSyncedAt),
    selected = selected,
    role = ""
)

fun Workspace.toEntity(selected: Boolean): WorkspaceEntity = WorkspaceEntity(
    id = id,
    userId = userId,
    name = name,
    lastSyncedAt = lastSync.toEpochMilliseconds(),
    icon = null,
    iconTint = null,
    selected = selected
)
