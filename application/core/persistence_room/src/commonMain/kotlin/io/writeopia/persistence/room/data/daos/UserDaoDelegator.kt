package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.UserCommonDao
import io.writeopia.persistence.room.extensions.toEntity
import io.writeopia.persistence.room.extensions.toModel
import io.writeopia.sdk.models.user.WriteopiaUser

class UserDaoDelegator(private val delegate: UserDao): UserCommonDao {

    override suspend fun getUser(userId: String): Pair<WriteopiaUser, Boolean> =
        delegate.getUserById(userId)?.toModel()
            ?: (WriteopiaUser.disconnectedUser() to false)

    override suspend fun insertUser(user: WriteopiaUser, selected: Boolean) {
        delegate.insertUser(user.toEntity(selected))
    }

    override suspend fun deleteUser(userId: String) {
        val user = getUser(userId).first

        if (user.id != WriteopiaUser.DISCONNECTED) {
            delegate.deleteUser(user.toEntity(selected = false))
        }
    }

    override suspend fun selectedCurrentUser(): WriteopiaUser =
        delegate.getCurrentUser()?.toModel()?.first ?: WriteopiaUser.disconnectedUser()

    override suspend fun unselectAllUsers() {
        delegate.unselectAllUsers()
    }
}
