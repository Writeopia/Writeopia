package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ForgotPasswordResponse(val success: Boolean, val message: String? = null)

@Serializable
data class PasswordVerifyCodeRequest(val email: String, val code: String)

@Serializable
data class PasswordResetWithCodeRequest(val email: String, val code: String, val newPassword: String)
