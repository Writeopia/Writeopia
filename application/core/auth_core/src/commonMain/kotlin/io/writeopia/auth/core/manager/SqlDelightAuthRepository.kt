package io.writeopia.auth.core.manager

import io.writeopia.auth.core.utils.toModel
import io.writeopia.common.utils.extensions.toLong
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sql.WriteopiaDb

internal class SqlDelightAuthRepository(
    private val writeopiaDb: WriteopiaDb?
) : AuthRepository {

    override suspend fun getUser(): WriteopiaUser =
        writeopiaDb?.writeopiaUserEntityQueries
            ?.selectCurrentUser()
            ?.executeAsOneOrNull()
            ?.toModel()
            ?: WriteopiaUser.disconnectedUser()

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
        writeopiaDb?.writeopiaUserEntityQueries
            ?.insertUser(
                id = user.id,
                name = user.name,
                email = user.email,
                selected = selected.toLong(),
                tier = user.tier.tierName()
            )
    }

    override suspend fun getAuthToken(): String? =
        writeopiaDb?.tokenEntityQueries
            ?.selectTokenByUserId(getUser().id)
            ?.executeAsOneOrNull()

    override suspend fun saveToken(userId: String, token: String) {
        writeopiaDb?.tokenEntityQueries
            ?.insertToken(userId, token)
    }

    override suspend fun useOffline() {
        val user = getUser()
        saveUser(user.copy(id = WriteopiaUser.OFFLINE), true)
    }
}
