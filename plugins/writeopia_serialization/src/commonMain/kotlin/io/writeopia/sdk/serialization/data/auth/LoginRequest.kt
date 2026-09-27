package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val identifier: String, val password: String)
