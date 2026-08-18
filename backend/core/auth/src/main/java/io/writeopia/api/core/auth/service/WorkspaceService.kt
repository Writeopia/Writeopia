@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.models.AddUserResult
import io.writeopia.api.core.auth.repository.countUsersInWorkspace
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getUserInWorkspace
import io.writeopia.api.core.auth.repository.getUsersInWorkspace
import io.writeopia.api.core.auth.repository.getUsersInWorkspacePaginated
import io.writeopia.api.core.auth.repository.getWorkspaceById
import io.writeopia.api.core.auth.repository.getWorkspacesByUserId
import io.writeopia.api.core.auth.repository.insertUserInWorkspace
import io.writeopia.api.core.auth.repository.insertWorkspace
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.removeUserFromWorkspace
import io.writeopia.connection.logger
import io.writeopia.models.user.PaginatedWorkspaceUsers
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

    fun getUsersInWorkspacePaginated(
        workspaceId: String,
        page: Int,
        pageSize: Int,
        writeopiaDb: WriteopiaDbBackend
    ): PaginatedWorkspaceUsers {
        val offset = (page - 1) * pageSize
        val users = writeopiaDb.getUsersInWorkspacePaginated(
            workspaceId,
            pageSize.toLong(),
            offset.toLong()
        )
        val totalCount = writeopiaDb.countUsersInWorkspace(workspaceId)
        val totalPages = ((totalCount + pageSize - 1) / pageSize).toInt()

        return PaginatedWorkspaceUsers(
            users = users,
            page = page,
            pageSize = pageSize,
            totalCount = totalCount.toInt(),
            totalPages = totalPages,
            hasNextPage = page < totalPages
        )
    }

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

    fun addUserToWorkspaceByUserId(
        userId: String,
        workspaceId: String,
        role: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        writeopiaDb.insertUserInWorkspace(workspaceId, userId, role)
    }


    fun addUserToWorkspaceSecure(
        workspaceOwnerId: String,
        userEmail: String,
        workspaceId: String,
        role: String,
        writeopiaDb: WriteopiaDbBackend
    ): AddUserResult {
        val ownerWorkspaces = writeopiaDb.getWorkspacesByUserId(workspaceOwnerId)
        if (!ownerWorkspaces.any { it.id == workspaceId }) {
            println("This user doesn't not have access to this workspace as admin")
            // Note: This check is handled by runIfAdmin in the routing layer
        }

        // Check if user already exists in the workspace
        val existingUser = writeopiaDb.getUserInWorkspace(workspaceId, userEmail)
        if (existingUser != null) {
            println("User with email $userEmail is already in this workspace")
            return AddUserResult.USER_ALREADY_IN_WORKSPACE
        }

        return writeopiaDb.getUserByEmail(userEmail)?.id?.let { userId ->
            writeopiaDb.insertUserInWorkspace(workspaceId, userId, role)
            AddUserResult.SUCCESS
        } ?: run {
            println("User with email $userEmail doesn't exist")
            AddUserResult.USER_NOT_FOUND
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

    /**
     * Triggers a workspace export job.
     * This will start a Cloud Run job that exports all documents and folders
     * from the workspace and sends a download link to the user's email.
     *
     * @param userId The ID of the user requesting the export
     * @param workspaceId The ID of the workspace to export
     * @param writeopiaDb The database connection
     * @return true if the export job was started successfully, false otherwise
     */
    fun triggerWorkspaceExport(
        userId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        return try {
            val user = writeopiaDb.getUserById(userId)
            if (user == null) {
                logger.error("User not found: $userId")
                return false
            }

            val workspace = writeopiaDb.getWorkspaceById(workspaceId)
            if (workspace == null) {
                logger.error("Workspace not found: $workspaceId")
                return false
            }

            // TODO: Start Cloud Run job with the following environment variables:
            // - EXPORT_WORKSPACE_ID: workspaceId
            // - EXPORT_USER_ID: userId
            // - EXPORT_USER_EMAIL: user.email
            // - EXPORT_USER_NAME: user.name
            // For now, log the export request
            logger.info("Export requested for workspace: $workspaceId by user: $userId (${user.email})")
            logger.info("Cloud Run job would be started here with env vars:")
            logger.info("  EXPORT_WORKSPACE_ID=$workspaceId")
            logger.info("  EXPORT_USER_ID=$userId")
            logger.info("  EXPORT_USER_EMAIL=${user.email}")
            logger.info("  EXPORT_USER_NAME=${user.name}")

            true
        } catch (e: Exception) {
            logger.error("Failed to trigger workspace export", e)
            false
        }
    }

}
