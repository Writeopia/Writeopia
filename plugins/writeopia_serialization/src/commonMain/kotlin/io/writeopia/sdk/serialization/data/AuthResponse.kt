package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(val token: String, val writeopiaUser: WriteopiaUserApi)
