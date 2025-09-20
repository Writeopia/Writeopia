package io.writeopia.api.core.auth

import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getWorkspacesByUserId
import io.writeopia.api.core.auth.repository.insertUserInWorkspace
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
        role: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean =
        writeopiaDb.getUserByEmail(userEmail)?.id?.let { userId ->
            writeopiaDb.insertUserInWorkspace(workspaceId, userId, role)
            true
        } ?: false
}
