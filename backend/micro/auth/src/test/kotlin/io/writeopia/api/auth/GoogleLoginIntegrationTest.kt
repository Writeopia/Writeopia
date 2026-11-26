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
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.api.core.auth.repository.legalAcceptancesOf
import io.writeopia.api.core.auth.legal.LegalDocuments
import io.writeopia.api.core.auth.repository.setUserStatusPendingDeletion
import io.writeopia.api.core.auth.service.GoogleCodeExchanger
import io.writeopia.api.core.auth.service.GoogleTokenVerifier
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.GoogleLoginRequest
import io.writeopia.sdk.serialization.data.auth.GoogleSignUpRequiredResponse
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
    private val otherEmail = "google.other@example.com"
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
        db.deleteUserByEmail(otherEmail)
    }

    @AfterTest
    fun tearDown() {
        db.deleteUserByEmail(testEmail)
        db.deleteUserByEmail(otherEmail)
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

    /** The second request of a first sign-in: the token back, with the username and the Terms. */
    private val signUp = GoogleLoginRequest(
        idToken = "id-token",
        username = "ana_silva",
        acceptedLegalVersion = LegalDocuments.VERSION,
    )

    @Test
    fun `first login asks for a username and creates nothing`() = testApplication {
        installApp()
        val client = defaultClient()

        val response = client.googleLogin()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val required = response.body<GoogleSignUpRequiredResponse>()
        assertEquals("id-token", required.idToken)
        assertEquals(testEmail, required.email)
        assertEquals("Ana Silva", required.name)
        assertEquals("googlelogin", required.suggestedUsername)
        assertNull(db.getUserByEmail(testEmail))
    }

    @Test
    fun `sign up creates an active user with the chosen username and a workspace`() = testApplication {
        installApp()
        val client = defaultClient()

        val response = client.googleLogin(signUp)

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
        assertEquals("ana_silva", user.username)
        assertEquals(user.id, db.getUserIdByExternalIdentity(PROVIDER_GOOGLE, subject))
        assertEquals(2, db.legalAcceptancesOf(user.id).size)

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

        val first = client.googleLogin(signUp).body<AuthResponse>()
        // The Google account's email changed since: the subject still finds the account.
        verifier.identity = identity.copy(email = otherEmail)
        val second = client.googleLogin().body<AuthResponse>()

        assertEquals(first.writeopiaUser.id, second.writeopiaUser.id)
        assertEquals(first.writeopiaUser.id, db.getUserIdByExternalIdentity(PROVIDER_GOOGLE, subject))
        assertEquals(testEmail, db.getUserByEmail(testEmail)?.email)
        assertNull(db.getUserByEmail(otherEmail))
        val workspaces = client.get("/api/workspace/user") {
            header("X-Forwarded-Authorization", "Bearer ${second.accessToken}")
        }.body<List<WorkspaceApi>>()
        assertEquals(1, workspaces.size)
    }

    @Test
    fun `matching email links the google identity to the existing account`() = testApplication {
        // A Workspace account of the email's own domain: Google vouches for the address.
        verifier.identity = identity.copy(hostedDomain = "example.com")
        installApp()
        val client = defaultClient()

        db.insertUser(
            name = "Existing User",
            username = "existing_user",
            email = testEmail,
            password = "hash",
            salt = "salt",
            status = UserStatus.EMAIL_CONFIRMATION_PENDING,
        )

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
    fun `an email google is not authoritative for is never linked to an existing account`() = testApplication {
        // A consumer Google account made with an example.com address: verified once, maybe not
        // owned by the same person anymore.
        installApp()
        val client = defaultClient()

        db.insertUser(
            name = "Existing User",
            username = "existing_user",
            email = testEmail,
            password = "hash",
            salt = "salt",
            status = UserStatus.ACTIVE,
        )

        val response = client.googleLogin()

        assertEquals(HttpStatusCode.PreconditionFailed, response.status)
        assertNull(db.getUserIdByExternalIdentity(PROVIDER_GOOGLE, subject))
    }

    @Test
    fun `account pending deletion is rejected with 403`() = testApplication {
        installApp()
        val client = defaultClient()

        val userId = client.googleLogin(signUp).body<AuthResponse>().writeopiaUser.id
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

        // No account yet: the exchanged token comes back, to sign up with.
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("exchanged-token", response.body<GoogleSignUpRequiredResponse>().idToken)
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

        val response = client.googleLogin(signUp, path = "/api/auth/login/google/web")

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

    @Test
    fun `sign up needs the current terms and a free valid username`() = testApplication {
        installApp()
        val client = defaultClient()
        db.insertUser(
            name = "Other",
            username = "taken_name",
            email = otherEmail,
            password = "hash",
            salt = "salt",
            status = UserStatus.ACTIVE,
        )

        val noTerms = client.googleLogin(signUp.copy(acceptedLegalVersion = null))
        val oldTerms = client.googleLogin(signUp.copy(acceptedLegalVersion = "2020-01-01"))
        val invalid = client.googleLogin(signUp.copy(username = "no spaces"))
        val taken = client.googleLogin(signUp.copy(username = "taken_name"))

        assertEquals(HttpStatusCode.BadRequest, noTerms.status)
        assertEquals(HttpStatusCode.BadRequest, oldTerms.status)
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertNull(db.getUserByEmail(testEmail))
    }
}
