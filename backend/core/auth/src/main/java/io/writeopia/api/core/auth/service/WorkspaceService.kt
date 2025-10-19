package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.getWorkspacesByUserId
import io.writeopia.api.core.auth.repository.insertUserInWorkspace
import io.writeopia.api.core.auth.repository.removeUserFromWorkspace
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sql.WriteopiaDbBackend

object WorkspaceService {

    fun getWorkspacesByUserEmail(
        userEmail: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<Workspace> =
        writeopiaDb.getUserByEmail(userEmail)
            ?.id
            ?.let(writeopiaDb::getWorkspacesByUserId)
            ?: emptyList()

    fun getWorkspacesByUserId(
        userId: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<Workspace> = writeopiaDb.getWorkspacesByUserId(userId)

    fun getUsersInWorkspace(
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<WriteopiaUser> {
        writeopiaDb.
    }

    fun addUserToWorkspaceAdmin(
        userEmail: String,
        workspaceId: String,
        role: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean =
        writeopiaDb.getUserByEmail(userEmail)?.id?.let { userId ->
            writeopiaDb.insertUserInWorkspace(workspaceId, userId, role)
            true
        } ?: false

    fun addUserToWorkspaceSecure(
        workspaceOwnerId: String,
        userEmail: String,
        workspaceId: String,
        role: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        val ownerWorkspaces = writeopiaDb.getWorkspacesByUserId(workspaceOwnerId)
        if (!ownerWorkspaces.any { it.id == workspaceId }) return false

        return writeopiaDb.getUserById(userEmail)?.id?.let { userId ->
            writeopiaDb.insertUserInWorkspace(workspaceId, userId, role)
            true
        } ?: false
    }

    suspend fun removeUserFromWorkspaceSecure(
        workspaceOwnerId: String,
        userId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        val ownerWorkspaces = writeopiaDb.getWorkspacesByUserId(workspaceOwnerId)
        if (!ownerWorkspaces.any { it.id == workspaceId }) return false

        return removeUserFromWorkspace(userId, workspaceId, writeopiaDb)
    }

    suspend fun removeUserFromWorkspace(
        userId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        writeopiaDb.removeUserFromWorkspace(workspaceId, userId)
        return true
    }

    suspend fun removeUserFromWorkspaceByEmail(
        userEmail: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        val user = writeopiaDb.getUserByEmail(userEmail) ?: return false
        writeopiaDb.removeUserFromWorkspace(workspaceId, user.id)
        return true
    }
}
