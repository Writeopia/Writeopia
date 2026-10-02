package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.models.GoogleIdentity
import io.writeopia.api.core.auth.models.GoogleTokenException
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.service.GoogleCodeExchanger
import io.writeopia.api.core.auth.service.GoogleTokenVerifier
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.GoogleLoginRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The Google route is also mounted in the gateway; this only checks the wiring. */
class GoogleLoginSmokeTest {

    private val db = configurePersistence()
    private val testEmail = "gateway.google@example.com"

    private val verifier = object : GoogleTokenVerifier {
        override suspend fun verify(idToken: String): GoogleIdentity =
            if (idToken == "good") {
                GoogleIdentity("sub-gw", testEmail, true, "Gateway User", "web-client")
            } else {
                throw GoogleTokenException("bad token")
            }
    }

    private val exchanger = object : GoogleCodeExchanger {
        override suspend fun exchange(code: String, codeVerifier: String?, redirectUri: String, clientId: String) =
            error("not used")
    }

    @BeforeTest
    fun setUp() {
        db.deleteUserByEmail(testEmail)
    }

    @AfterTest
    fun tearDown() {
        db.deleteUserByEmail(testEmail)
    }

    @Test
    fun `google login is mounted in the gateway`() = testApplication {
        application {
            module(db, debugMode = true, googleTokenVerifier = verifier, googleCodeExchanger = exchanger)
        }
        val client = defaultClient()

        val response = client.post("/api/auth/login/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleLoginRequest(idToken = "good"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.body<AuthResponse>().accessToken)
        assertEquals(UserStatus.ACTIVE, db.getUserByEmail(testEmail)!!.status)

        val rejected = client.post("/api/auth/login/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleLoginRequest(idToken = "bad"))
        }
        assertEquals(HttpStatusCode.Unauthorized, rejected.status)
    }
}
