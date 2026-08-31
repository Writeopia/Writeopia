package io.writeopia.sdk.serialization.data.auth

import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val accessToken: String?,
    val refreshToken: String? = null,
    val writeopiaUser: WriteopiaUserApi,
    val enabled: Boolean = true
)
