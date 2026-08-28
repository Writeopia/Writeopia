package io.writeopia.sdk.network.oauth

interface BearerTokenHandler {
    suspend fun getIdToken(): String?

    suspend fun getRefreshToken(): String?

    suspend fun refreshTokens(): TokenRefreshResult
}

sealed class TokenRefreshResult {
    data class Success(val accessToken: String, val refreshToken: String) : TokenRefreshResult()

    data object Failure : TokenRefreshResult()

    data object NoRefreshToken : TokenRefreshResult()
}
