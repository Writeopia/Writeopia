package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class EmailConfirmRequest(val email: String, val code: String)

@Serializable
data class EmailResendRequest(val email: String)

@Serializable
data class EmailConfirmResponse(val success: Boolean, val message: String? = null)
