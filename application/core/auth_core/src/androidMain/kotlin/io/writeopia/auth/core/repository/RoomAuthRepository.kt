package io.writeopia.auth.core.repository

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.persistence.daos.UserCommonDao
import io.writeopia.sdk.models.user.WriteopiaUser

class RoomAuthRepository(private val userDao: UserCommonDao) : AuthRepository {
    override suspend fun getUser(): WriteopiaUser = userDao.selectCurrentUser()

    override suspend fun isLoggedIn(): Boolean = getUser().id != WriteopiaUser.DISCONNECTED

    override suspend fun logout(): ResultData<Boolean> {
        if (isLoggedIn()) {
            userDao.deleteUser(getUser().id)
        }

        return ResultData.Complete(true)
    }

    override suspend fun saveUser(user: WriteopiaUser, selected: Boolean) {
        userDao.insertUser(user, selected)
    }

    override suspend fun saveToken(userId: String, token: String) { }

    override suspend fun getAuthToken(): String? = ""

    override suspend fun useOffline() {
        saveUser(WriteopiaUser.disconnectedUser().copy(id = WriteopiaUser.OFFLINE), true)
    }
}
