package io.writeopia.auth.core.manager

import io.writeopia.common.utils.ResultData
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sql.WriteopiaDb

internal class SqlDelightRepository(private val writeopiaDb: WriteopiaDb?) : AuthRepository {

    override suspend fun getUser(): WriteopiaUser =
        WriteopiaUser.disconnectedUser()

    override suspend fun isLoggedIn(): ResultData<Boolean> = ResultData.Complete(false)

    override suspend fun logout(): ResultData<Boolean> = ResultData.Complete(true)
}
