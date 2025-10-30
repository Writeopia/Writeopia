package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)
