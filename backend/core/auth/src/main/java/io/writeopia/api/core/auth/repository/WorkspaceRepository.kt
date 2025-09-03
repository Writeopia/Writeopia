package io.writeopia.api.core.auth.repository

import io.writeopia.sdk.models.Workspace
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Clock
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

fun WriteopiaDbBackend.getWorkspaceByUserId(id: String): Workspace? {
    return this.workspaceEntityQueries.getWorkspaceById(id)
        .executeAsOneOrNull()
        ?.let { entity ->
            Workspace(
                id = entity.id,
                userId = "",
                name = entity.name,
                lastSync = Instant.DISTANT_PAST,
                selected = false
            )
        }
}

fun WriteopiaDbBackend.insertWorkspace(workspace: Workspace) {
    this.workspaceEntityQueries.insert(
        id = workspace.id,
        name = workspace.name,
        icon = null,
        icon_tint = null
    )
}

fun WriteopiaDbBackend.insertUserInWorkspace(workspace: Workspace) {
//    this.workspaceEntityQueries.insertUser(
//        workspace_id = workspace.id,
//        user_id = workspace.userId
//    )
}

fun WriteopiaDbBackend.getWorkspacesByUserId(userId: String): List<Workspace> =
    this.workspaceEntityQueries
        .getWorkspacesByUserId(userId)
        .executeAsList()
        .map { entity ->
            Workspace(
                id = entity.id,
                userId = entity.user_id,
                name = entity.name,
                lastSync = Clock.System.now(),
                selected = false
            )
        }
