package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String
)
