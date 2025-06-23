package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AuthRepository : UserRepository {

    override fun listenForUser(): Flow<WriteopiaUser> = flow { emit(getUser()) }

    override suspend fun getUser(): WriteopiaUser

    suspend fun isLoggedIn(): Boolean

    suspend fun logout(): ResultData<Boolean>

    suspend fun saveUser(user: WriteopiaUser, selected: Boolean)

    suspend fun saveToken(userId: String, token: String)

    suspend fun getAuthToken(): String?

    suspend fun useOffline()
}
