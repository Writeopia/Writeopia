package io.writeopia.common.utils.persistence.daos

import io.writeopia.sdk.models.user.WriteopiaUser

interface UserCommonDao {

    suspend fun getUser(userId: String): Pair<WriteopiaUser, Boolean>

    suspend fun insertUser(user: WriteopiaUser, selected: Boolean)

    suspend fun deleteUser(userId: String)

    suspend fun selectedCurrentUser(): WriteopiaUser

    suspend fun unselectAllUsers()
}
