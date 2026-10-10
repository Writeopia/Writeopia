package io.writeopia.api.gateway

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.repository.deleteEmailVerification
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.core.auth.repository.getEmailVerification
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.api.core.auth.service.EmailVerificationService
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.serialization.data.auth.EmailConfirmRequest
import io.writeopia.sdk.serialization.data.auth.EmailConfirmResponse
import io.writeopia.sdk.serialization.data.auth.EmailResendRequest
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.data.auth.RegisterResponse
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegisterEmailVerificationIntegrationTest {

    private val db = configurePersistence()
    private val password = "lasjbdalsdq08w9y&"

    private fun randomEmail() = "verify_${Random.nextInt(1_000_000)}@gmail.com"

    private fun registerRequest(email: String, code: String?) = RegisterRequest(
        name = "Name",
        email = email,
        username = "user_${Random.nextInt(1_000_000)}",
        workspaceName = "workspace name",
        password = password,
        verificationCode = code,
    )

    @Test
    fun `the full flow verifies the email first and then creates an active account`() =
        testApplication {
            application { module(db, debugMode = false) }

            val client = defaultClient()
            val email = randomEmail()

            try {
                val sendResponse = client.sendRegisterCode(email)
                assertEquals(HttpStatusCode.OK, sendResponse.status)

                // Nothing is created before the email is verified.
                assertNull(db.getUserByEmail(email))
                val code = assertNotNull(db.getEmailVerification(email)).code

                val verifyResponse = client.verifyRegisterCode(email, code)
                assertEquals(HttpStatusCode.OK, verifyResponse.status)
                assertTrue(verifyResponse.body<EmailConfirmResponse>().success)
                assertNull(db.getUserByEmail(email))

                val registerResponse = client.register(registerRequest(email, code))
                assertEquals(HttpStatusCode.Created, registerResponse.status)

                val body = registerResponse.body<RegisterResponse>()
                assertFalse(body.emailConfirmationRequired)
                assertNotNull(body.accessToken)
                assertNotNull(body.refreshToken)

                assertEquals(UserStatus.ACTIVE, db.getUserByEmail(email)?.status)
                // The code is consumed by the registration.
                assertNull(db.getEmailVerification(email))

                // The account can log in straight away, even outside debug mode.
                val loginResponse = client.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(email, password))
                }
                assertEquals(HttpStatusCode.OK, loginResponse.status)
                val login = loginResponse.body<AuthResponse>()
                assertTrue(login.enabled)
                assertNotNull(login.accessToken)
            } finally {
                db.deleteUserByEmail(email)
                db.deleteEmailVerification(email)
            }
        }

    @Test
    fun `registering without a verification code is rejected and creates nothing`() =
        testApplication {
            application { module(db, debugMode = true) }

            val client = defaultClient()
            val email = randomEmail()

            try {
                val response = client.register(registerRequest(email, code = null))

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertNull(db.getUserByEmail(email))
            } finally {
                db.deleteUserByEmail(email)
            }
        }

    @Test
    fun `registering with a wrong verification code is rejected and creates nothing`() =
        testApplication {
            application { module(db, debugMode = true) }

            val client = defaultClient()
            val email = randomEmail()

            try {
                client.sendRegisterCode(email)
                val code = assertNotNull(db.getEmailVerification(email)).code
                val wrongCode = if (code == "111111") "222222" else "111111"

                val response = client.register(registerRequest(email, wrongCode))

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertNull(db.getUserByEmail(email))
            } finally {
                db.deleteUserByEmail(email)
                db.deleteEmailVerification(email)
            }
        }

    @Test
    fun `a code cannot be used to register a different email`() = testApplication {
        application { module(db, debugMode = true) }

        val client = defaultClient()
        val verifiedEmail = randomEmail()
        val otherEmail = randomEmail()

        try {
            client.sendRegisterCode(verifiedEmail)
            val code = assertNotNull(db.getEmailVerification(verifiedEmail)).code

            val response = client.register(registerRequest(otherEmail, code))

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertNull(db.getUserByEmail(otherEmail))
        } finally {
            db.deleteEmailVerification(verifiedEmail)
            db.deleteUserByEmail(otherEmail)
        }
    }

    @Test
    fun `asking for a code for a registered email looks the same and sends nothing`() =
        testApplication {
            application { module(db, debugMode = true) }

            val client = defaultClient()
            val registeredEmail = randomEmail()
            val unknownEmail = randomEmail()

            try {
                db.insertUser(
                    name = "Existing",
                    username = "existing_${Random.nextInt(1_000_000)}",
                    email = registeredEmail,
                    password = "password",
                    salt = "salt",
                    status = UserStatus.ACTIVE,
                )

                val unknownResponse = client.sendRegisterCode(unknownEmail)
                val registeredResponse = client.sendRegisterCode(registeredEmail)

                assertEquals(HttpStatusCode.OK, registeredResponse.status)
                assertEquals(unknownResponse.status, registeredResponse.status)
                assertEquals(unknownResponse.bodyAsText(), registeredResponse.bodyAsText())

                assertNull(db.getEmailVerification(registeredEmail))
                assertNotNull(db.getEmailVerification(unknownEmail))
            } finally {
                db.deleteUserByEmail(registeredEmail)
                db.deleteEmailVerification(unknownEmail)
            }
        }

    @Test
    fun `asking again right away does not replace the code`() = testApplication {
        application { module(db, debugMode = true) }

        val client = defaultClient()
        val email = randomEmail()

        try {
            client.sendRegisterCode(email)
            val first = assertNotNull(db.getEmailVerification(email))

            val secondResponse = client.sendRegisterCode(email)

            assertEquals(HttpStatusCode.OK, secondResponse.status)
            assertEquals(first, db.getEmailVerification(email))
        } finally {
            db.deleteEmailVerification(email)
        }
    }

    @Test
    fun `an invalid email is rejected when asking for a code`() = testApplication {
        application { module(db, debugMode = true) }

        val response = defaultClient().sendRegisterCode("not-an-email")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `the right code stops working after too many wrong attempts`() = testApplication {
        application { module(db, debugMode = true) }

        val client = defaultClient()
        val email = randomEmail()

        try {
            client.sendRegisterCode(email)
            val code = assertNotNull(db.getEmailVerification(email)).code
            val wrongCode = if (code == "111111") "222222" else "111111"

            repeat(EmailVerificationService.MAX_ATTEMPTS) {
                assertEquals(
                    HttpStatusCode.BadRequest,
                    client.verifyRegisterCode(email, wrongCode).status
                )
            }

            assertEquals(HttpStatusCode.BadRequest, client.verifyRegisterCode(email, code).status)
            assertEquals(
                HttpStatusCode.BadRequest,
                client.register(registerRequest(email, code)).status
            )
            assertNull(db.getUserByEmail(email))
        } finally {
            db.deleteUserByEmail(email)
            db.deleteEmailVerification(email)
        }
    }

    private suspend fun HttpClient.sendRegisterCode(email: String) =
        post("/api/auth/register/email/send") {
            contentType(ContentType.Application.Json)
            setBody(EmailResendRequest(email))
        }

    private suspend fun HttpClient.verifyRegisterCode(email: String, code: String) =
        post("/api/auth/register/email/verify") {
            contentType(ContentType.Application.Json)
            setBody(EmailConfirmRequest(email, code))
        }

    private suspend fun HttpClient.register(request: RegisterRequest) =
        post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
}
