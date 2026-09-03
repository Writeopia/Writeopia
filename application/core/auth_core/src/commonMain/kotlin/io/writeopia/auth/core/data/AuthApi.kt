package io.writeopia.auth.core.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.DeleteAccountResponse
import io.writeopia.sdk.serialization.data.auth.EmailConfirmRequest
import io.writeopia.sdk.serialization.data.auth.EmailConfirmResponse
import io.writeopia.sdk.serialization.data.auth.EmailResendRequest
import io.writeopia.sdk.serialization.data.auth.ForgotPasswordRequest
import io.writeopia.sdk.serialization.data.auth.ForgotPasswordResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.ManageUserRequest
import io.writeopia.sdk.serialization.data.auth.PasswordResetWithCodeRequest
import io.writeopia.sdk.serialization.data.auth.PasswordVerifyCodeRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.data.auth.RefreshTokenRequest
import io.writeopia.sdk.serialization.data.auth.RegisterResponse
import io.writeopia.sdk.serialization.data.auth.TokenRefreshResponse
import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import io.writeopia.sdk.serialization.data.auth.ResetPasswordRequest

class AuthApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun login(email: String, password: String): ResultData<AuthResponse> = try {
        val response = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body<AuthResponse>()

        ResultData.Complete(response)
    } catch (e: Exception) {
        println("login error: ${e.message}")
        e.printStackTrace()
        ResultData.Error(e)
    }

    /**
     * Web-specific login that uses HttpOnly cookies for token storage.
     * The backend sets the tokens in HttpOnly cookies instead of returning them in the response body.
     */
    suspend fun loginWeb(email: String, password: String): ResultData<AuthResponse> = try {
        val response = client.post("$baseUrl/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body<AuthResponse>()

        ResultData.Complete(response)
    } catch (e: Exception) {
        println("loginWeb error: ${e.message}")
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun register(
        name: String,
        email: String,
        workspaceName: String,
        password: String
    ): ResultData<RegisterResponse> = try {
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(name, email, workspaceName, password))
        }.body<RegisterResponse>()

        ResultData.Complete(response)
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun resetPassword(newPassword: String, token: String): ResultData<Unit> = try {
        val response = client.put("$baseUrl/api/auth/password/reset") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(newPassword))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun deleteAccount(token: String): ResultData<Boolean> = try {
        val response = client.delete("$baseUrl/api/auth/account") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<DeleteAccountResponse>()

        ResultData.Complete(response.deleted)
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    suspend fun enableUser(email: String, adminKey: String): ResultData<Unit> = try {
        val response = client.post("$baseUrl/api/auth/admin/enable-user") {
            contentType(ContentType.Application.Json)
            header("X-Admin-Key", adminKey)
            setBody(ManageUserRequest(email))
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        println("enableUser error: ${e.message}")
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun confirmEmail(email: String, code: String): ResultData<AuthResponse> = try {
        val response = client.post("$baseUrl/api/auth/email/confirm") {
            contentType(ContentType.Application.Json)
            setBody(EmailConfirmRequest(email, code))
        }

        if (response.status.isSuccess()) {
            val authResponse = response.body<AuthResponse>()
            ResultData.Complete(authResponse)
        } else {
            val errorResponse = response.body<EmailConfirmResponse>()
            ResultData.Error(Exception(errorResponse.message ?: "Invalid code"))
        }
    } catch (e: Exception) {
        println("confirmEmail error: ${e.message}")
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun resendConfirmationEmail(email: String): ResultData<Boolean> = try {
        val response = client.post("$baseUrl/api/auth/email/resend") {
            contentType(ContentType.Application.Json)
            setBody(EmailResendRequest(email))
        }.body<EmailConfirmResponse>()

        if (response.success) {
            ResultData.Complete(true)
        } else {
            ResultData.Error(Exception(response.message ?: "Failed to resend"))
        }
    } catch (e: Exception) {
        println("resendConfirmationEmail error: ${e.message}")
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun requestPasswordReset(email: String): ResultData<Boolean> = try {
        val response = client.post("$baseUrl/api/auth/password/forgot") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(email))
        }.body<ForgotPasswordResponse>()

        if (response.success) {
            ResultData.Complete(true)
        } else {
            ResultData.Error(Exception(response.message ?: "Failed to send reset code"))
        }
    } catch (e: Exception) {
        println("requestPasswordReset error: ${e.message}")
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun verifyPasswordResetCode(email: String, code: String): ResultData<Boolean> = try {
        val response = client.post("$baseUrl/api/auth/password/verify-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordVerifyCodeRequest(email, code))
        }.body<ForgotPasswordResponse>()

        if (response.success) {
            ResultData.Complete(true)
        } else {
            ResultData.Error(Exception(response.message ?: "Invalid code"))
        }
    } catch (e: Exception) {
        println("verifyPasswordResetCode error: ${e.message}")
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String): ResultData<Boolean> = try {
        val response = client.post("$baseUrl/api/auth/password/reset-with-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetWithCodeRequest(email, code, newPassword))
        }.body<ForgotPasswordResponse>()

        if (response.success) {
            ResultData.Complete(true)
        } else {
            ResultData.Error(Exception(response.message ?: "Failed to reset password"))
        }
    } catch (e: Exception) {
        println("resetPasswordWithCode error: ${e.message}")
        e.printStackTrace()
        ResultData.Error(e)
    }

    /**
     * Verifies the current user's token against the backend.
     * Returns:
     * - ResultData.Complete with user data if token is valid
     * - ResultData.Error with null exception if token is invalid (401/403)
     * - ResultData.Error with exception if network error occurred
     */
    suspend fun getCurrentUser(token: String): ResultData<WriteopiaUserApi> = try {
        val response = client.get("$baseUrl/api/auth/user/current") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(response.body<WriteopiaUserApi>())
        } else {
            // HTTP error (401, 403, etc.) - token is invalid
            // Return Error with null exception to distinguish from network errors
            ResultData.Error()
        }
    } catch (e: Exception) {
        // Network error - return with exception to indicate connectivity issue
        println("getCurrentUser error: ${e.message}")
        ResultData.Error(e)
    }

    suspend fun refreshToken(refreshToken: String): ResultData<TokenRefreshResponse> = try {
        val response = client.post("$baseUrl/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken))
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(response.body<TokenRefreshResponse>())
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        println("refreshToken error: ${e.message}")
        ResultData.Error(e)
    }

    suspend fun logout(refreshToken: String): ResultData<Unit> = try {
        val response = client.post("$baseUrl/api/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken))
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        println("logout error: ${e.message}")
        ResultData.Error(e)
    }
}
