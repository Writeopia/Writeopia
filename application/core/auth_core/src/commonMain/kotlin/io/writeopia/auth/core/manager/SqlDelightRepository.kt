package io.writeopia.auth.core.manager

import io.writeopia.auth.core.utils.toModel
import io.writeopia.common.utils.ResultData
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sql.WriteopiaDb

internal class SqlDelightRepository(private val writeopiaDb: WriteopiaDb?) : AuthRepository {

    override suspend fun getUser(): WriteopiaUser =
        writeopiaDb?.writeopiaUserEntityQueries
            ?.selectCurrentUser()
            ?.executeAsOneOrNull()
            ?.toModel()
            ?: WriteopiaUser.disconnectedUser()

    override suspend fun isLoggedIn(): ResultData<Boolean> =
        ResultData.Complete(
            writeopiaDb?.writeopiaUserEntityQueries
                ?.selectCurrentUser()
                ?.executeAsOneOrNull()
                ?.toModel() == null
        )

    override suspend fun logout(): ResultData<Boolean> {
        getUser()?.let { user ->
            writeopiaDb?.writeopiaUserEntityQueries
                ?.insertUser(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    password = user.password,
                    selected = 0,
                )
        }

        return ResultData.Complete(true)
    }
}
