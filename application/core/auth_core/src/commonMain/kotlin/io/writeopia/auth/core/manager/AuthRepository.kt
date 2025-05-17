package io.writeopia.auth.core.manager

import io.writeopia.common.utils.ResultData
import io.writeopia.sdk.models.user.WriteopiaUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AuthRepository {

    fun listenForUser(): Flow<WriteopiaUser> = flow { emit(getUser()) }

    suspend fun getUser(): WriteopiaUser

    suspend fun isLoggedIn(): ResultData<Boolean>

    suspend fun logout(): ResultData<Boolean>
}
