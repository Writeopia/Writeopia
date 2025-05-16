package io.writeopia.api.gateway

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.model.LoginRequest
import io.writeopia.api.core.auth.model.RegisterRequest
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthIntegrationTest {

    private val db = configurePersistence()

    @AfterTest
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
    fun `it should be possible to register an user and receive a token`() = testApplication {
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

        val response1 = client.post("api/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequest("email@gmail.com", "lasjbdalsdq08w9y&")
            )
        }

        assertEquals(response1.status, HttpStatusCode.OK)
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
}
