package io.writeopia.api.core.auth

import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.hash.toBase64
import io.writeopia.api.core.auth.models.WriteopiaBeUser
import io.writeopia.api.core.auth.repository.getCompanyByDomain
import io.writeopia.api.core.auth.repository.getWorkspacesByUserId
import io.writeopia.api.core.auth.repository.insertCompany
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.api.core.auth.repository.insertUserInWorkspace
import io.writeopia.api.core.auth.repository.insertWorkspace
import io.writeopia.sdk.models.Workspace
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Clock
import java.util.UUID

object AuthService {
    fun getWorkspaceForUser(
        writeopiaDb: WriteopiaDbBackend,
        userId: String,
        userName: String
    ): Workspace {
        val workspaces = writeopiaDb.getWorkspacesByUserId(userId)

        return if (workspaces.isNotEmpty()) {
            workspaces.first()
        } else {
            val workspace = Workspace(
                id = UUID.randomUUID().toString(),
                userId = userId,
                "$userName Workspace",
                lastSync = Clock.System.now(),
                selected = false
            )

            writeopiaDb.insertWorkspace(workspace)
            writeopiaDb.insertUserInWorkspace(workspaceId = workspace.id, userId = userId, role = "ADMIN")

            workspace
        }
    }

    fun createUser(
        writeopiaDb: WriteopiaDbBackend,
        registerRequest: RegisterRequest,
        enabled: Boolean
    ): WriteopiaUser {
        val (name, email, workspace, password) = registerRequest

        val id = UUID.randomUUID().toString()

        val salt = HashUtils.generateSalt()
        val hash = HashUtils.hashPassword(password, salt).toBase64()

        writeopiaDb.insertUser(
            id = id,
            name = name,
            email = email,
            password = hash,
            salt = salt.toBase64(),
            workspace = workspace,
            enabled = enabled
        )

        return WriteopiaUser(
            id = id,
            name = name,
            email = email,
            company = ""
        )
    }

    fun resetPassword(
        writeopiaDb: WriteopiaDbBackend,
        user: WriteopiaBeUser,
        newPassword: String
    ) {
        val salt = HashUtils.generateSalt()
        val hash = HashUtils.hashPassword(newPassword, salt).toBase64()

        writeopiaDb.insertUser(user.copy(password = hash, salt = salt.toBase64()))
    }
}
