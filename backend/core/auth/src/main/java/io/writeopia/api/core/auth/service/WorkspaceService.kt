@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.service

import com.google.cloud.run.v2.EnvVar
import com.google.cloud.run.v2.JobName
import com.google.cloud.run.v2.JobsClient
import com.google.cloud.run.v2.RunJobRequest
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

    // Cloud Run Job configuration - read from environment variables
    private val gcpProject: String? = System.getenv("GCP_PROJECT")
    private val gcpRegion: String? = System.getenv("GCP_REGION")
    private val exportJobName: String = System.getenv("EXPORT_JOB_NAME") ?: "writeopia-export"

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
                logger.error("[Export] User not found: $userId")
                return false
            }

            val workspace = writeopiaDb.getWorkspaceById(workspaceId)
            if (workspace == null) {
                logger.error("[Export] Workspace not found: $workspaceId")
                return false
            }

            // Check if GCP configuration is available
            if (gcpProject == null || gcpRegion == null) {
                logger.error("[Export] GCP_PROJECT or GCP_REGION environment variables not set")
                logger.error("[Export] GCP_PROJECT: $gcpProject, GCP_REGION: $gcpRegion")
                return false
            }

            logger.info("[Export] Triggering Cloud Run Job...")
            logger.info("[Export] Project: $gcpProject, Region: $gcpRegion, Job: $exportJobName")
            logger.info("[Export] User: ${user.email}, Workspace: $workspaceId")

            // Create the job client and run the job
            JobsClient.create().use { jobsClient ->
                val jobName = JobName.of(gcpProject, gcpRegion, exportJobName)

                // Build environment variable overrides
                val envVars = listOf(
                    EnvVar.newBuilder().setName("EXPORT_WORKSPACE_ID").setValue(workspaceId).build(),
                    EnvVar.newBuilder().setName("EXPORT_USER_ID").setValue(userId).build(),
                    EnvVar.newBuilder().setName("EXPORT_USER_EMAIL").setValue(user.email).build(),
                    EnvVar.newBuilder().setName("EXPORT_USER_NAME").setValue(user.name).build()
                )

                // Create the run job request with environment overrides
                val request = RunJobRequest.newBuilder()
                    .setName(jobName.toString())
                    .setOverrides(
                        RunJobRequest.Overrides.newBuilder()
                            .addContainerOverrides(
                                RunJobRequest.Overrides.ContainerOverride.newBuilder()
                                    .addAllEnv(envVars)
                                    .build()
                            )
                            .build()
                    )
                    .build()

                // Execute the job asynchronously (non-blocking)
                val operation = jobsClient.runJobAsync(request)
                logger.info("[Export] Job triggered successfully. Operation name: ${operation.name}")
            }

            true
        } catch (e: Exception) {
            logger.error("[Export] Failed to trigger workspace export", e)
            false
        }
    }

}
