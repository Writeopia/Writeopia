package io.writeopia.auth.core.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.common.utils.ResultData
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.DeleteAccountResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.data.auth.ResetPasswordRequest

class AuthApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun login(email: String, password: String): ResultData<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/api/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }.body<AuthResponse>()

            ResultData.Complete(response)
        } catch (e: Exception) {
            ResultData.Error(e)
        }
    }

    suspend fun register(
        name: String,
        email: String,
        company: String,
        password: String
    ): ResultData<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/api/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(name, email, company, password))
            }.body<AuthResponse>()

            ResultData.Complete(response)
        } catch (e: Exception) {
            ResultData.Error(e)
        }
    }

    suspend fun resetPassword(newPassword: String, token: String) : ResultData<Unit> {
        return try {
            val response = client.put("$baseUrl/api/password/reset") {
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
    }

    suspend fun deleteAccount(token: String): ResultData<Boolean> {
        return try {
            val response = client.delete("$baseUrl/api/account") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<DeleteAccountResponse>()

            ResultData.Complete(response.deleted)
        } catch (e: Exception) {
            ResultData.Error(e)
        }
    }
}
