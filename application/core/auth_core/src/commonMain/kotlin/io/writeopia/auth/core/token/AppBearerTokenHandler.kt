package io.writeopia.auth.core.token

import io.writeopia.sdk.network.oauth.BearerTokenHandler
import io.writeopia.sdk.network.oauth.TokenRefreshResult

expect object AppBearerTokenHandler : BearerTokenHandler {
    override suspend fun getIdToken(): String?
    override suspend fun getRefreshToken(): String?
    override suspend fun refreshTokens(): TokenRefreshResult
}
