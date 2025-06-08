package io.writeopia.sdk.repository

import io.writeopia.sdk.models.user.WriteopiaUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface UserRepository {
    fun listenForUser(): Flow<WriteopiaUser> = flow { emit(getUser()) }

    suspend fun getUser(): WriteopiaUser
}
