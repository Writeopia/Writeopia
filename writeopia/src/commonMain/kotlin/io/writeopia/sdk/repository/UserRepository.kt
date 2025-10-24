package io.writeopia.sdk.repository

import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface UserRepository {
    fun listenForUser(): Flow<WriteopiaUser> = flow { emit(getUser()) }

    suspend fun getUser(): WriteopiaUser

    suspend fun getWorkspace(): Workspace
}
