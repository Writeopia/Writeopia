package io.writeopia.auth.core.repository

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.persistence.daos.TokenCommonDao
import io.writeopia.common.utils.persistence.daos.UserCommonDao
import io.writeopia.common.utils.persistence.daos.WorkspaceCommonDao
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData

class RoomAuthRepository(
    private val userDao: UserCommonDao,
    private val tokenCommonDao: TokenCommonDao,
    private val workspaceCommonDao: WorkspaceCommonDao
) : AuthRepository {
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

    override suspend fun saveToken(userId: String, token: String) {
        tokenCommonDao.saveToken(token = token, userId = userId)
    }

    override suspend fun getAuthToken(): String? = tokenCommonDao.getTokenByUserId(getUser().id)

    override suspend fun useOffline() {
        saveUser(WriteopiaUser.disconnectedUser().copy(id = WriteopiaUser.DISCONNECTED), true)
    }

    override suspend fun getWorkspace(): Workspace =
        workspaceCommonDao.selectCurrentWorkspace() ?: Workspace.disconnectedWorkspace()

    override suspend fun saveWorkspace(workspace: Workspace) {
        workspaceCommonDao.insertWorkspace(workspace, true)
    }
}
