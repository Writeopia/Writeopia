@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.token

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.network.oauth.BearerTokenHandler
import io.writeopia.sdk.network.oauth.TokenRefreshResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TokenManager(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi
) : BearerTokenHandler {

    private val refreshMutex = Mutex()

    private var lastSessionRefreshAt = 0L

    override suspend fun getIdToken(): String? =
        authRepository.getAccessToken()

    override suspend fun getRefreshToken(): String? =
        authRepository.getRefreshToken()

    /**
     * Web sessions live in HttpOnly cookies, so they're renewed by the backend rotating the
     * cookies instead of by handing tokens back. The backend rotates the refresh token on
     * every call, so concurrent 401s are serialized and a caller that waited on a refresh
     * finished meanwhile reuses it instead of rotating again.
     */
    override suspend fun refreshSession(): Boolean {
        if (!authRepository.useWebLogin) return false

        val requestedAt = Clock.System.now().toEpochMilliseconds()

        return refreshMutex.withLock {
            if (lastSessionRefreshAt > requestedAt) return@withLock true

            val refreshed = authApi.refreshWeb() is ResultData.Complete
            if (refreshed) lastSessionRefreshAt = Clock.System.now().toEpochMilliseconds()
            refreshed
        }
    }

    override suspend fun refreshTokens(): TokenRefreshResult =
        refreshMutex.withLock {
            val refreshToken = authRepository.getRefreshToken()
                ?: return@withLock TokenRefreshResult.NoRefreshToken

            when (val result = authApi.refreshToken(refreshToken)) {
                is ResultData.Complete -> {
                    val tokenResponse = result.data
                    val userId = authRepository.getUser().id

                    // Calculate expiry time (15 minutes from now, with 1-minute buffer)
                    val expiresAt = Clock.System.now().toEpochMilliseconds() + (14 * 60 * 1000L)

                    authRepository.saveTokens(
                        userId = userId,
                        accessToken = tokenResponse.accessToken,
                        refreshToken = tokenResponse.refreshToken,
                        expiresAt = expiresAt
                    )

                    TokenRefreshResult.Success(
                        accessToken = tokenResponse.accessToken,
                        refreshToken = tokenResponse.refreshToken
                    )
                }
                is ResultData.Error -> TokenRefreshResult.Failure
                else -> TokenRefreshResult.Failure
            }
        }
}
