package io.writeopia.auth.core.token

import io.writeopia.sdk.network.oauth.BearerTokenHandler
import io.writeopia.sdk.network.oauth.TokenRefreshResult

actual object AppBearerTokenHandler : BearerTokenHandler {
    actual override suspend fun getIdToken(): String? = "mock"

    actual override suspend fun getRefreshToken(): String? = null

    actual override suspend fun refreshTokens(): TokenRefreshResult = TokenRefreshResult.NoRefreshToken
}
