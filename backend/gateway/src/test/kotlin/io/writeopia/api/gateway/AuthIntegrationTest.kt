package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.DeleteAccountRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthIntegrationTest {

    private val db = configurePersistence()

    @BeforeTest
    fun setUp() {
        db.deleteUserByEmail("email@gmail.com")
    }

    @Test
    fun `it should be possible to register an user`() = testApplication {
        application {
            module(db)
        }

        val client = defaultClient()

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&"
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)
    }

    @Test
    fun `it should not be possible create 2 users with the same email`() = testApplication {
        application {
            module(db)
        }

        val client = defaultClient()

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&"
                )
            )
        }

        val response1 = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&"
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)
        assertEquals(response1.status, HttpStatusCode.Conflict)
    }

    @Test
    fun `it should be possible to delete your account, if your logged in`() = testApplication {
        application {
            module(db)
        }

        val client = defaultClient()
        val password = "lasjbdalsdq08w9y&"

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = password
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)

        val response1 = client.post("api/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("email@gmail.com", password))
        }

        assertEquals(response1.status, HttpStatusCode.OK)

        val token = response1.body<AuthResponse>().token!!

        val response2 = client.delete("api/account") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(response2.status, HttpStatusCode.OK)
    }

    @Test
    fun `it should be possible to delete your account, if don't have the right token`() = testApplication {
        application {
            module(db)
        }

        val response2 = client.delete("api/account") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer asdasdasd")
        }

        assertEquals(response2.status, HttpStatusCode.Unauthorized)
    }
}
