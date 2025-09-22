package io.writeopia.api.core.auth.repository

import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal fun WriteopiaDbBackend.listWorkspaces(): List<Workspace> {
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

internal fun WriteopiaDbBackend.getWorkspaceByUserId(id: String): Workspace? {
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

internal fun WriteopiaDbBackend.insertWorkspace(workspace: Workspace) {
    this.workspaceEntityQueries.insert(
        id = workspace.id,
        name = workspace.name,
        icon = null,
        icon_tint = null
    )
}

internal fun WriteopiaDbBackend.insertUserInWorkspace(
    workspaceId: String,
    userId: String,
    role: String
) {
    this.workspaceToUserQueries.insertWorkspaceToUser(
        workspace_id = workspaceId,
        user_id = userId,
        role = role
    )
}

internal fun WriteopiaDbBackend.getWorkspacesByUserId(userId: String): List<Workspace> =
    this.workspaceEntityQueries
        .getWorkspacesByUserId(userId)
        .executeAsList()
        .map { entity ->
            Workspace(
                id = entity.workspace_id,
                userId = entity.user_id,
                name = entity.workspace_name,
                lastSync = Clock.System.now(),
                selected = false,
                role = entity.user_role
            )
        }

internal suspend fun WriteopiaDbBackend.removeUserFromWorkspace(workspaceId: String, userId: String) {
    this.workspaceToUserQueries.removeUserFromWorkspace(workspaceId, userId)
}
