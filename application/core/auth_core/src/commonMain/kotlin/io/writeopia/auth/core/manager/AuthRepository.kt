package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class TokenData(
    val accessToken: String,
    val refreshToken: String?,
    val accessTokenExpiresAt: Long?
)

interface AuthRepository : UserRepository {

    override fun listenForUser(): Flow<WriteopiaUser> = flow { emit(getUser()) }

    override fun listenForWorkspace(): Flow<Workspace> = flow {
        emit(getWorkspace() ?: Workspace.disconnectedWorkspace())
    }

    /**
     * Returns true if the platform should use web-specific login (with HttpOnly cookies).
     * Default implementation returns false for non-web platforms.
     */
    val useWebLogin: Boolean
        get() = false

    override suspend fun getUser(): WriteopiaUser

    suspend fun isLoggedIn(): Boolean

    suspend fun logout(): ResultData<Boolean>

    suspend fun saveUser(user: WriteopiaUser, selected: Boolean)

    suspend fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    )

    suspend fun getAuthToken(): String?

    suspend fun getAccessToken(): String? = getAuthToken()

    suspend fun getRefreshToken(): String?

    suspend fun getTokenData(): TokenData?

    suspend fun isAccessTokenExpired(): Boolean

    suspend fun clearTokens()

    suspend fun useOffline()

    override suspend fun getWorkspace(): Workspace?

    suspend fun saveWorkspace(workspace: Workspace)

    /**
     * The workspaces of the user saved by the last successful [cacheWorkspaces], to be shown
     * before (or without) the backend answering. Their document count is unknown
     * ([Workspace.UNKNOWN_DOCUMENT_COUNT]). Platforms that keep nothing on the device return none.
     */
    suspend fun getCachedWorkspaces(userId: String): List<Workspace> = emptyList()

    /**
     * Saves the workspaces the backend listed for the user. Known workspaces keep their sync state
     * and selection, only the name is refreshed; the ones no longer listed (and not selected) go.
     */
    suspend fun cacheWorkspaces(userId: String, workspaces: List<Workspace>) {}

    suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long)

    suspend fun unselectAllWorkspaces()

    suspend fun unselectAllUsers()

    suspend fun savePendingConfirmationEmail(email: String)

    suspend fun getPendingConfirmationEmail(): String?

    suspend fun clearPendingConfirmationEmail()

    suspend fun saveForgotPasswordEmail(email: String)

    suspend fun getForgotPasswordEmail(): String?

    suspend fun saveForgotPasswordCode(code: String)

    suspend fun getForgotPasswordCode(): String?

    suspend fun clearForgotPasswordData()
}
