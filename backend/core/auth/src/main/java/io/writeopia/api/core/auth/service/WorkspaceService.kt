@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getUserInWorkspace
import io.writeopia.api.core.auth.repository.getUsersInWorkspace
import io.writeopia.api.core.auth.repository.getWorkspaceById
import io.writeopia.api.core.auth.repository.getWorkspacesByUserId
import io.writeopia.api.core.auth.repository.insertUserInWorkspace
import io.writeopia.api.core.auth.repository.insertWorkspace
import io.writeopia.api.core.auth.repository.removeUserFromWorkspace
import io.writeopia.models.user.WorkspaceUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
    ): List<WorkspaceUser> = writeopiaDb.getUsersInWorkspace(workspaceId)

    fun getUserInWorkspace(
        workspaceId: String,
        userEmail: String,
        writeopiaDb: WriteopiaDbBackend
    ): WorkspaceUser? = writeopiaDb.getUserInWorkspace(workspaceId, userEmail)

    fun createWorkspace(
        workspaceId: String,
        workspaceName: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        writeopiaDb.insertWorkspace(
            Workspace(
                id = workspaceId,
                userId = "",
                name = workspaceName,
                lastSync = Instant.DISTANT_PAST,
                selected = false,
                role = ""
            )
        )
    }

    fun addUserToWorkspaceAdmin(
        userEmail: String,
        workspaceId: String,
        role: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        val user = writeopiaDb.getUserByEmail(userEmail)
        val workspace = writeopiaDb.getWorkspaceById(workspaceId)

        if (user != null && workspace != null) {
            writeopiaDb.insertUserInWorkspace(workspaceId, user.id, role)
            return true
        } else {
            return false
        }
    }


    fun addUserToWorkspaceSecure(
        workspaceOwnerId: String,
        userEmail: String,
        workspaceId: String,
        role: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        val ownerWorkspaces = writeopiaDb.getWorkspacesByUserId(workspaceOwnerId)
        if (!ownerWorkspaces.any { it.id == workspaceId }) {
            println("This user doesn't not have access to this workspace as admin")
            false
        }

        return writeopiaDb.getUserByEmail(userEmail)?.id?.let { userId ->
            writeopiaDb.insertUserInWorkspace(workspaceId, userId, role)
            true
        } ?: run {
            println("User with email $userEmail doesn't exist")
            false
        }
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
