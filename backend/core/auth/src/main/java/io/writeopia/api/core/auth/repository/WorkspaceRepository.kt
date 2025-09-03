package io.writeopia.api.core.auth.repository

import io.writeopia.sdk.models.Workspace
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Instant

fun WriteopiaDbBackend.listWorkspaces(): List<Workspace> {
    return this.workspaceEntityQueries.getWorkspaces()
        .executeAsList()
        .map { entity ->
            Workspace(
                id = entity.id,
                userId = "",
                name = entity.name,
                lastSync = Instant.DISTANT_PAST,
                selected = false
            )
        }
}
