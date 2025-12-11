package io.writeopia.api.core.auth.repository

import io.writeopia.models.user.WorkspaceUser
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.Instant

internal fun WriteopiaDbBackend.listWorkspaces(): List<Workspace> {
    return this.workspaceEntityQueries.getWorkspaces()
        .executeAsList()
        .map { entity ->
            Workspace(
                id = entity.id,
                userId = "",
                name = entity.name,
                lastSync = Instant.DISTANT_PAST,
                selected = false,
                role = ""
            )
        }
}

internal fun WriteopiaDbBackend.getWorkspaceById(workspaceId: String): Workspace? {
    return this.workspaceEntityQueries.getWorkspaceById(workspaceId)
        .executeAsOneOrNull()
        ?.let { entity ->
            Workspace(
                id = entity.id,
                userId = "",
                name = entity.name,
                lastSync = Instant.DISTANT_PAST,
                selected = false,
                role = ""
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

internal fun WriteopiaDbBackend.isUserInWorkspace(userId: String, workspaceId: String): Boolean =
    this.workspaceEntityQueries
        .getWorkspacesByUserId(userId)
        .executeAsList()
        .any { entity -> entity.workspace_id == workspaceId }


internal fun WriteopiaDbBackend.isUserAdminInWorkspace(
    userId: String,
    workspaceId: String
): Boolean =
    this.workspaceEntityQueries
        .getWorkspacesByUserIdIfAdmin(userId)
        .executeAsList()
        .any { entity -> entity.workspace_id == workspaceId }

internal fun WriteopiaDbBackend.getUsersInWorkspace(workspaceId: String): List<WorkspaceUser> =
    this.workspaceToUserQueries
        .getUsersInWorkspace(workspaceId)
        .executeAsList()
        .map { workspaceToUser ->
            WorkspaceUser(
                id = workspaceToUser.user_entity_id,
                email = workspaceToUser.user_email,
                name = workspaceToUser.user_name,
                role = workspaceToUser.role
            )
        }

internal fun WriteopiaDbBackend.getUserInWorkspace(
    workspaceId: String,
    userEmail: String
): WorkspaceUser? =
    this.workspaceToUserQueries
        .getUserInWorkspace(workspaceId, userEmail)
        .executeAsOneOrNull()
        ?.let { workspaceToUser ->
            WorkspaceUser(
                id = workspaceToUser.user_entity_id,
                email = workspaceToUser.user_email,
                name = workspaceToUser.user_name,
                role = workspaceToUser.role
            )
        }

internal suspend fun WriteopiaDbBackend.removeUserFromWorkspace(
    workspaceId: String,
    userId: String
) {
    this.workspaceToUserQueries.removeUserFromWorkspace(workspaceId, userId)
}

fun WriteopiaDbBackend.changeWorkspaceName(workspaceId: String, newName: String) {
    this.workspaceEntityQueries.changeName(newName, workspaceId)
}

fun WriteopiaDbBackend.changeWorkspaceRoleForUser(
    workspaceId: String,
    userId: String,
    newRole: String
) {
    this.workspaceToUserQueries.changeRole(
        workspace_id = workspaceId,
        user_id = userId,
        role = newRole
    )
}
