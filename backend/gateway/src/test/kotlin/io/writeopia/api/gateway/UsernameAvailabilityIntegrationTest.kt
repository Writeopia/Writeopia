package io.writeopia.api.gateway

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.serialization.data.auth.UsernameAvailabilityResponse
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsernameAvailabilityIntegrationTest {

    private val db = configurePersistence()

    @Test
    fun `a free username is available`() = testApplication {
        application { module(db, debugMode = true) }

        val response = defaultClient().checkUsername("free_${Random.nextInt(1_000_000)}")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<UsernameAvailabilityResponse>().available)
    }

    @Test
    fun `a taken username is not available`() = testApplication {
        application { module(db, debugMode = true) }

        val username = "taken_${Random.nextInt(1_000_000)}"
        val email = "$username@gmail.com"

        try {
            db.insertUser(
                name = "Existing",
                username = username,
                email = email,
                password = "password",
                salt = "salt",
                status = UserStatus.ACTIVE,
            )

            val response = defaultClient().checkUsername(username)

            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(response.body<UsernameAvailabilityResponse>().available)
        } finally {
            db.deleteUserByEmail(email)
        }
    }

    @Test
    fun `an invalid username is rejected`() = testApplication {
        application { module(db, debugMode = true) }

        val client = defaultClient()

        assertEquals(HttpStatusCode.BadRequest, client.checkUsername("ab").status)
        assertEquals(HttpStatusCode.BadRequest, client.checkUsername("has space").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/auth/username/available").status)
    }

    private suspend fun HttpClient.checkUsername(username: String) =
        get("/api/auth/username/available") {
            parameter("username", username)
        }
}
