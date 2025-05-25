package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequest(val newPassword: String)
