package io.writeopia.sdk.network.oauth

interface BearerTokenHandler {
    suspend fun getIdToken(): String?

    suspend fun getRefreshToken(): String?

    suspend fun refreshTokens(): TokenRefreshResult

    /**
     * Renews a session whose tokens the client can't read, like the web app's HttpOnly
     * cookies. Called when a request is rejected with 401; returning true retries that
     * request once. Clients holding their own tokens keep this default and refresh through
     * [refreshTokens] instead.
     */
    suspend fun refreshSession(): Boolean = false
}

sealed class TokenRefreshResult {
    data class Success(val accessToken: String, val refreshToken: String) : TokenRefreshResult()

    data object Failure : TokenRefreshResult()

    data object NoRefreshToken : TokenRefreshResult()
}
