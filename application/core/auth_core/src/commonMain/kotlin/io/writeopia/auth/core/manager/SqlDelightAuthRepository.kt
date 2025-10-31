package io.writeopia.auth.core.manager

import io.writeopia.auth.core.utils.toModel
import io.writeopia.common.utils.extensions.toBoolean
import io.writeopia.common.utils.extensions.toLong
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sql.WriteopiaDb
import kotlinx.datetime.Instant

internal class SqlDelightAuthRepository(
    private val writeopiaDb: WriteopiaDb?
) : AuthRepository {

    override suspend fun getUser(): WriteopiaUser {
        return writeopiaDb?.writeopiaUserEntityQueries
            ?.selectCurrentUser()
            ?.executeAsOneOrNull()
            ?.toModel()
            ?: WriteopiaUser.disconnectedUser()
    }

    override suspend fun isLoggedIn(): Boolean = getAuthToken() != null

    override suspend fun logout(): ResultData<Boolean> {
        getUser().let { user ->
            writeopiaDb?.writeopiaUserEntityQueries
                ?.insertUser(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    selected = 0,
                    tier = user.tier.name
                )
        }

        return ResultData.Complete(true)
    }

    override suspend fun saveUser(user: WriteopiaUser, selected: Boolean) {
        println("saving user: ${user.id}, selected: ${selected.toLong()}")

        writeopiaDb?.writeopiaUserEntityQueries?.run {
            unselectAllUsers()
            insertUser(
                id = user.id,
                name = user.name,
                email = user.email,
                selected = selected.toLong(),
                tier = user.tier.tierName()
            )
        }
    }

    override suspend fun getAuthToken(): String? {
        return writeopiaDb?.tokenEntityQueries
            ?.selectTokenByUserId(getUser().id)
            ?.executeAsOneOrNull()
    }

    override suspend fun saveToken(userId: String, token: String) {
        writeopiaDb?.tokenEntityQueries?.insertToken(userId, token)
    }

    override suspend fun useOffline() {
        val user = getUser()
        saveUser(user.copy(id = WriteopiaUser.DISCONNECTED), true)
    }

    override suspend fun getWorkspace(): Workspace =
        writeopiaDb?.workspaceEntityQueries
            ?.selectCurrentWorkspace()
            ?.executeAsOneOrNull()
            ?.let { entity ->
                Workspace(
                    id = entity.id,
                    userId = entity.user_id,
                    name = entity.name,
                    lastSync = Instant.fromEpochMilliseconds(entity.last_synced_at),
                    selected = entity.selected.toBoolean(),
                    role = ""
                )
            } ?: Workspace.disconnectedWorkspace()

    override suspend fun saveWorkspace(workspace: Workspace) {
        writeopiaDb?.workspaceEntityQueries
            ?.insert(
                id = workspace.id,
                user_id = workspace.userId,
                name = workspace.name,
                last_synced_at = workspace.lastSync.toEpochMilliseconds(),
                icon = null,
                icon_tint = null,
                selected = workspace.selected.toLong(),
            )
    }

    override suspend fun unselectAllWorkspaces() {
        writeopiaDb?.workspaceEntityQueries?.unselectAll()
    }
}
