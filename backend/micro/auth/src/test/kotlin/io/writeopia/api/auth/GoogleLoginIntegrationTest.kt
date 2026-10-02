package io.writeopia.api.auth

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.models.GoogleIdentity
import io.writeopia.api.core.auth.models.GoogleTokenException
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.repository.PROVIDER_GOOGLE
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getUserIdByExternalIdentity
import io.writeopia.api.core.auth.repository.setUserStatusPendingDeletion
import io.writeopia.api.core.auth.service.GoogleCodeExchanger
import io.writeopia.api.core.auth.service.GoogleTokenVerifier
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.GoogleLoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleLoginIntegrationTest {

    private val db = configureTestPersistence()
    private val testEmail = "google.login@example.com"
    private val subject = "google-sub-123"

    private val identity = GoogleIdentity(
        subject = subject,
        email = testEmail.uppercase(),
        emailVerified = true,
        name = "Ana Silva",
        audience = "web-client",
    )

    private class FakeVerifier(var identity: GoogleIdentity?) : GoogleTokenVerifier {
        var lastToken: String? = null

        override suspend fun verify(idToken: String): GoogleIdentity {
            lastToken = idToken
            return identity ?: throw GoogleTokenException("invalid for test")
        }
    }

    private class FakeExchanger(private val idToken: String) : GoogleCodeExchanger {
        var lastCall: List<String?>? = null

        override suspend fun exchange(code: String, codeVerifier: String?, redirectUri: String, clientId: String): String {
            lastCall = listOf(code, codeVerifier, redirectUri, clientId)
            return idToken
        }
    }

    private val verifier = FakeVerifier(identity)
    private val exchanger = FakeExchanger("exchanged-token")

    @BeforeTest
    fun setUp() {
        db.deleteUserByEmail(testEmail)
    }

    @AfterTest
    fun tearDown() {
        db.deleteUserByEmail(testEmail)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installApp() {
        application {
            module(db, debugMode = true, googleTokenVerifier = verifier, googleCodeExchanger = exchanger)
        }
    }

    private suspend fun io.ktor.client.HttpClient.googleLogin(
        request: GoogleLoginRequest = GoogleLoginRequest(idToken = "id-token"),
        path: String = "/api/auth/login/google",
    ) = post(path) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    @Test
    fun `first login creates an active user with a workspace`() = testApplication {
        installApp()
        val client = defaultClient()

        val response = client.googleLogin()

        assertEquals(HttpStatusCode.OK, response.status)
        val auth = response.body<AuthResponse>()
        assertNotNull(auth.accessToken)
        assertNotNull(auth.refreshToken)
        assertTrue(auth.enabled)
        assertEquals(testEmail, auth.writeopiaUser.email)
        assertEquals("Ana Silva", auth.writeopiaUser.name)
        assertEquals("id-token", verifier.lastToken)

        val user = db.getUserByEmail(testEmail)
        assertNotNull(user)
        assertEquals(UserStatus.ACTIVE, user.status)
        assertEquals("googlelogin", user.username)
        assertEquals(user.id, db.getUserIdByExternalIdentity(PROVIDER_GOOGLE, subject))

        val workspaces = client.get("/api/workspace/user") {
            header("X-Forwarded-Authorization", "Bearer ${auth.accessToken}")
        }.body<List<WorkspaceApi>>()
        assertEquals(1, workspaces.size)
        assertEquals("Ana's Workspace", workspaces.single().name)
    }

    @Test
    fun `second login with the same subject reuses the user`() = testApplication {
        installApp()
        val client = defaultClient()

        val first = client.googleLogin().body<AuthResponse>()
        val second = client.googleLogin().body<AuthResponse>()

        assertEquals(first.writeopiaUser.id, second.writeopiaUser.id)
        val workspaces = client.get("/api/workspace/user") {
            header("X-Forwarded-Authorization", "Bearer ${second.accessToken}")
        }.body<List<WorkspaceApi>>()
        assertEquals(1, workspaces.size)
    }

    @Test
    fun `matching email links the google identity to the existing account`() = testApplication {
        installApp()
        val client = defaultClient()

        val registered = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "Existing Workspace",
                    name = "Existing User",
                    email = testEmail,
                    username = "existing_user",
                    password = "password123!",
                )
            )
        }
        assertEquals(HttpStatusCode.Created, registered.status)
        assertEquals(UserStatus.EMAIL_CONFIRMATION_PENDING, db.getUserByEmail(testEmail)!!.status)

        val response = client.googleLogin()

        assertEquals(HttpStatusCode.OK, response.status)
        val auth = response.body<AuthResponse>()
        val user = db.getUserByEmail(testEmail)!!
        assertEquals(user.id, auth.writeopiaUser.id)
        assertEquals("Existing User", auth.writeopiaUser.name)
        assertEquals("existing_user", user.username)
        assertEquals(UserStatus.ACTIVE, user.status)
        assertEquals(user.id, db.getUserIdByExternalIdentity(PROVIDER_GOOGLE, subject))
    }

    @Test
    fun `account pending deletion is rejected with 403`() = testApplication {
        installApp()
        val client = defaultClient()

        val userId = client.googleLogin().body<AuthResponse>().writeopiaUser.id
        runBlocking { db.setUserStatusPendingDeletion(userId) }

        val response = client.googleLogin()

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `invalid token is rejected with 401`() = testApplication {
        verifier.identity = null
        installApp()
        val client = defaultClient()

        val response = client.googleLogin()

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertNull(db.getUserByEmail(testEmail))
    }

    @Test
    fun `unverified email is rejected with 401`() = testApplication {
        verifier.identity = identity.copy(emailVerified = false)
        installApp()
        val client = defaultClient()

        val response = client.googleLogin()

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertNull(db.getUserByEmail(testEmail))
    }

    @Test
    fun `empty request is rejected with 400`() = testApplication {
        installApp()
        val client = defaultClient()

        val response = client.googleLogin(GoogleLoginRequest())

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `authorization code is exchanged before verification`() = testApplication {
        installApp()
        val client = defaultClient()

        val response = client.googleLogin(
            GoogleLoginRequest(
                code = "auth-code",
                codeVerifier = "pkce-verifier",
                redirectUri = "http://127.0.0.1:5555/callback",
                clientId = "desktop-client",
            )
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            listOf("auth-code", "pkce-verifier", "http://127.0.0.1:5555/callback", "desktop-client"),
            exchanger.lastCall
        )
        assertEquals("exchanged-token", verifier.lastToken)
    }

    @Test
    fun `code without redirect uri or client id is rejected with 401`() = testApplication {
        installApp()
        val client = defaultClient()

        val response = client.googleLogin(GoogleLoginRequest(code = "auth-code"))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertNull(exchanger.lastCall)
    }

    @Test
    fun `web variant sets cookies and keeps tokens out of the body`() = testApplication {
        installApp()
        val client = cookieClient()

        val response = client.googleLogin(path = "/api/auth/login/google/web")

        assertEquals(HttpStatusCode.OK, response.status)
        val auth = response.body<AuthResponse>()
        assertNull(auth.accessToken)
        assertNull(auth.refreshToken)
        assertEquals(testEmail, auth.writeopiaUser.email)

        val cookieNames = response.headers.getAll("Set-Cookie")!!.map { it.substringBefore("=") }
        assertTrue("writeopia_access" in cookieNames)
        assertTrue("writeopia_refresh" in cookieNames)
        assertTrue("writeopia_session" in cookieNames)
    }
}
