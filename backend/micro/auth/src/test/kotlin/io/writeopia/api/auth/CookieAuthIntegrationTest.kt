package io.writeopia.api.auth

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.core.auth.routing.SessionStatusResponse
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CookieAuthIntegrationTest {

    private val db = configureTestPersistence()
    private val testEmail = "cookietest@example.com"
    private val testPassword = "testPassword123!"

    @BeforeTest
    fun setUp() {
        db.deleteUserByEmail(testEmail)
    }

    @AfterTest
    fun tearDown() {
        db.deleteUserByEmail(testEmail)
    }

    private fun registerTestUser(client: io.ktor.client.HttpClient) = kotlinx.coroutines.runBlocking {
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "Test Workspace",
                    name = "Test User",
                    email = testEmail,
                    password = testPassword,
                )
            )
        }
    }

    @Test
    fun `web login should return user info without tokens in body`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = cookieClient()
        registerTestUser(client)

        val loginResponse = client.post("/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, testPassword))
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val authResponse = loginResponse.body<AuthResponse>()
        // Web login should NOT return tokens in response body (they're in cookies)
        assertNull(authResponse.accessToken)
        assertNull(authResponse.refreshToken)
        // But should return user info
        assertNotNull(authResponse.writeopiaUser)
        assertEquals(testEmail, authResponse.writeopiaUser.email)
    }

    @Test
    fun `web login should set HttpOnly cookies`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = cookieClient()
        registerTestUser(client)

        val loginResponse = client.post("/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, testPassword))
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)

        // Verify cookies are set in response headers
        val setCookieHeaders = loginResponse.headers.getAll("Set-Cookie")
        assertNotNull(setCookieHeaders)
        assertTrue(setCookieHeaders.isNotEmpty())

        // Check for expected cookies
        val cookieNames = setCookieHeaders.map { it.substringBefore("=") }
        assertTrue(cookieNames.contains("writeopia_access"), "Should set writeopia_access cookie")
        assertTrue(cookieNames.contains("writeopia_refresh"), "Should set writeopia_refresh cookie")
        assertTrue(cookieNames.contains("writeopia_session"), "Should set writeopia_session cookie")

        // Verify HttpOnly flag on access token cookie
        val accessCookie = setCookieHeaders.find { it.startsWith("writeopia_access=") }
        assertNotNull(accessCookie)
        assertTrue(accessCookie.contains("HttpOnly", ignoreCase = true), "Access token cookie should be HttpOnly")
    }

    @Test
    fun `web login with invalid credentials should return 401`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = cookieClient()
        registerTestUser(client)

        val loginResponse = client.post("/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, "wrongpassword"))
        }

        assertEquals(HttpStatusCode.Unauthorized, loginResponse.status)
    }

    @Test
    fun `session status should return authenticated after web login`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = cookieClient()
        registerTestUser(client)

        // Login via web endpoint
        val loginResponse = client.post("/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, testPassword))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        // Check session status - cookies should be sent automatically by cookieClient
        val statusResponse = client.get("/api/auth/session/status")

        assertEquals(HttpStatusCode.OK, statusResponse.status)
        val sessionStatus = statusResponse.body<SessionStatusResponse>()
        assertTrue(sessionStatus.authenticated)
        assertNotNull(sessionStatus.userId)
    }

    @Test
    fun `session status should return unauthenticated without login`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = cookieClient()

        val statusResponse = client.get("/api/auth/session/status")

        assertEquals(HttpStatusCode.OK, statusResponse.status)
        val sessionStatus = statusResponse.body<SessionStatusResponse>()
        assertEquals(false, sessionStatus.authenticated)
    }

    @Test
    fun `web logout should clear cookies`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = cookieClient()
        registerTestUser(client)

        // Login
        val loginResponse = client.post("/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, testPassword))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        // Verify authenticated
        val statusBeforeLogout = client.get("/api/auth/session/status").body<SessionStatusResponse>()
        assertTrue(statusBeforeLogout.authenticated)

        // Logout
        val logoutResponse = client.post("/api/auth/logout/web")
        assertEquals(HttpStatusCode.OK, logoutResponse.status)

        // Verify cookies are cleared (maxAge=0)
        val setCookieHeaders = logoutResponse.headers.getAll("Set-Cookie")
        assertNotNull(setCookieHeaders)
        val accessCookie = setCookieHeaders.find { it.startsWith("writeopia_access=") }
        assertNotNull(accessCookie)
        assertTrue(
            accessCookie.contains("Max-Age=0", ignoreCase = true),
            "Access cookie should be cleared with Max-Age=0"
        )
    }

    @Test
    fun `web refresh should work with cookie-based refresh token`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = cookieClient()
        registerTestUser(client)

        // Login via web endpoint
        val loginResponse = client.post("/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, testPassword))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        // Refresh via web endpoint - should use cookie automatically
        val refreshResponse = client.post("/api/auth/refresh/web")
        assertEquals(HttpStatusCode.OK, refreshResponse.status)

        // Verify new cookies are set
        val setCookieHeaders = refreshResponse.headers.getAll("Set-Cookie")
        assertNotNull(setCookieHeaders)
        assertTrue(setCookieHeaders.any { it.startsWith("writeopia_access=") })
    }

    @Test
    fun `authenticated endpoint should work with cookie auth`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = cookieClient()
        registerTestUser(client)

        // Login via web endpoint
        val loginResponse = client.post("/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, testPassword))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        // Access authenticated endpoint using cookie
        val workspacesResponse = client.get("/api/workspace/user")

        assertEquals(HttpStatusCode.OK, workspacesResponse.status)
    }

    @Test
    fun `authenticated endpoint should fail without cookie or bearer token`() = testApplication {
        application {
            module(db, debugMode = false) // debugMode=false to enforce auth
        }

        val client = defaultClient() // No cookie support

        val workspacesResponse = client.get("/api/workspace/user")

        assertEquals(HttpStatusCode.Unauthorized, workspacesResponse.status)
    }
}
