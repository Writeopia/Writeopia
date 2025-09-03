package io.writeopia.api.core.auth

import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getWorkspacesByUserId
import io.writeopia.sdk.models.Workspace
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

    fun addUserToWorkspace(
        userEmail: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        val userId = writeopiaDb.getUserByEmail(userEmail)?.id
        val workspace = writeopiaDb.getWorkspaceById(workspaceId)

    }
}
