package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.updateConfirmationCode
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.serialization.data.auth.ForgotPasswordRequest
import io.writeopia.sdk.serialization.data.auth.ForgotPasswordResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.PasswordResetWithCodeRequest
import io.writeopia.sdk.serialization.data.auth.PasswordVerifyCodeRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordResetIntegrationTest {

    private val db = configurePersistence()
    private val testEmail = "passwordreset@test.com"
    private val testPassword = "originalPassword123"
    private val testCode = "123456"

    @BeforeTest
    fun setUp() {
        db.deleteUserByEmail(testEmail)
    }

    @AfterTest
    fun tearDown() {
        db.deleteUserByEmail(testEmail)
    }

    private suspend fun registerAndEnableUser(client: io.ktor.client.HttpClient) {
        // Register user
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

        // In debug mode, the user is enabled automatically
    }

    @Test
    fun `forgot password endpoint should return success for existing user`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        val response = client.post("/api/auth/password/forgot") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(email = testEmail))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ForgotPasswordResponse>()
        assertTrue(body.success)
    }

    @Test
    fun `forgot password endpoint should return success for non-existing user for security`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        // Don't register any user, just request password reset
        val response = client.post("/api/auth/password/forgot") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(email = "nonexistent@example.com"))
        }

        // Should still return success for security reasons (not revealing if email exists)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ForgotPasswordResponse>()
        assertTrue(body.success)
    }

    @Test
    fun `verify code endpoint should return success for valid code`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        // Set a known confirmation code
        val codeExpiry = System.currentTimeMillis() + 15 * 60 * 1000 // 15 minutes from now
        db.updateConfirmationCode(testEmail, testCode, codeExpiry)

        val response = client.post("/api/auth/password/verify-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordVerifyCodeRequest(email = testEmail, code = testCode))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ForgotPasswordResponse>()
        assertTrue(body.success)
    }

    @Test
    fun `verify code endpoint should return error for invalid code`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        // Set a known confirmation code
        val codeExpiry = System.currentTimeMillis() + 15 * 60 * 1000
        db.updateConfirmationCode(testEmail, testCode, codeExpiry)

        // Try with wrong code
        val response = client.post("/api/auth/password/verify-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordVerifyCodeRequest(email = testEmail, code = "999999"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ForgotPasswordResponse>()
        assertTrue(!body.success)
    }

    @Test
    fun `verify code endpoint should return error for expired code`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        // Set an expired confirmation code
        val codeExpiry = System.currentTimeMillis() - 1000 // 1 second in the past
        db.updateConfirmationCode(testEmail, testCode, codeExpiry)

        val response = client.post("/api/auth/password/verify-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordVerifyCodeRequest(email = testEmail, code = testCode))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ForgotPasswordResponse>()
        assertTrue(!body.success)
    }

    @Test
    fun `reset password with code should update password successfully`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        // Set a known confirmation code
        val codeExpiry = System.currentTimeMillis() + 15 * 60 * 1000
        db.updateConfirmationCode(testEmail, testCode, codeExpiry)

        val newPassword = "newSecurePassword456"

        val response = client.post("/api/auth/password/reset-with-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetWithCodeRequest(email = testEmail, code = testCode, newPassword = newPassword))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ForgotPasswordResponse>()
        assertTrue(body.success)

        // Verify user can login with new password
        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, newPassword))
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
    }

    @Test
    fun `reset password with code should fail for invalid code`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        // Set a known confirmation code
        val codeExpiry = System.currentTimeMillis() + 15 * 60 * 1000
        db.updateConfirmationCode(testEmail, testCode, codeExpiry)

        val response = client.post("/api/auth/password/reset-with-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetWithCodeRequest(email = testEmail, code = "wrongcode", newPassword = "newPassword"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ForgotPasswordResponse>()
        assertTrue(!body.success)
    }

    @Test
    fun `reset password with code should fail for non-existing user`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val response = client.post("/api/auth/password/reset-with-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetWithCodeRequest(email = "nonexistent@example.com", code = testCode, newPassword = "newPassword"))
        }

        // Should fail since user doesn't exist (code can't be valid)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `code should be cleared after successful password reset`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        // Set a known confirmation code
        val codeExpiry = System.currentTimeMillis() + 15 * 60 * 1000
        db.updateConfirmationCode(testEmail, testCode, codeExpiry)

        val newPassword = "newSecurePassword456"

        // Reset password
        val response1 = client.post("/api/auth/password/reset-with-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetWithCodeRequest(email = testEmail, code = testCode, newPassword = newPassword))
        }

        assertEquals(HttpStatusCode.OK, response1.status)

        // Try to reset again with same code - should fail because code was cleared
        val response2 = client.post("/api/auth/password/reset-with-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetWithCodeRequest(email = testEmail, code = testCode, newPassword = "anotherPassword"))
        }

        assertEquals(HttpStatusCode.BadRequest, response2.status)
    }

    @Test
    fun `old password should not work after password reset`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        // Set a known confirmation code
        val codeExpiry = System.currentTimeMillis() + 15 * 60 * 1000
        db.updateConfirmationCode(testEmail, testCode, codeExpiry)

        val newPassword = "newSecurePassword456"

        // Reset password
        val response = client.post("/api/auth/password/reset-with-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetWithCodeRequest(email = testEmail, code = testCode, newPassword = newPassword))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // Try to login with old password - should fail
        val loginWithOldPassword = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, testPassword))
        }

        assertEquals(HttpStatusCode.Unauthorized, loginWithOldPassword.status)
    }

    @Test
    fun `full forgot password flow should work end to end`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        registerAndEnableUser(client)

        // Step 1: Request password reset
        val forgotResponse = client.post("/api/auth/password/forgot") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(email = testEmail))
        }
        assertEquals(HttpStatusCode.OK, forgotResponse.status)

        // Get the generated code from database
        val user = db.getUserByEmail(testEmail)
        val generatedCode = user?.confirmationCode ?: throw AssertionError("Code should be set")

        // Step 2: Verify code
        val verifyResponse = client.post("/api/auth/password/verify-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordVerifyCodeRequest(email = testEmail, code = generatedCode))
        }
        assertEquals(HttpStatusCode.OK, verifyResponse.status)

        // Step 3: Reset password
        val newPassword = "completelyNewPassword789"
        val resetResponse = client.post("/api/auth/password/reset-with-code") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetWithCodeRequest(email = testEmail, code = generatedCode, newPassword = newPassword))
        }
        assertEquals(HttpStatusCode.OK, resetResponse.status)

        // Step 4: Login with new password
        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, newPassword))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
    }
}
