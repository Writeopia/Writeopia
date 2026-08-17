package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.core.auth.repository.enableUserByEmail
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.updateUserTier
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffRequest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Tests for tier-based access control on premium endpoints.
 *
 * These tests verify that:
 * 1. FREE tier users receive 402 Payment Required on sync endpoints
 * 2. PREMIUM tier users can access sync endpoints successfully
 * 3. Non-members receive 403 Forbidden
 */
@OptIn(ExperimentalTime::class)
class TierVerificationTest {

    private val db = configurePersistence()
    private val adminKey = "testadminkey"
    private val testPassword = "testPassword123!"

    private fun generateTestEmail() = "tier_test_${System.nanoTime()}_${Random.nextInt()}@test.com"

    @Test
    fun `FREE tier user should receive 402 Payment Required when sending documents`() = testApplication {
        val testEmail = generateTestEmail()

        application {
            module(db, debugMode = false, adminKey = adminKey)
        }

        try {
            val client = defaultClient()

            // Register a new user (default tier is FREE)
            val registerResponse = client.post("/api/auth/register") {
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

            assertTrue(registerResponse.status.isSuccess(), "Registration failed: ${registerResponse.bodyAsText()}")

            // Enable the user (users are created disabled and need email confirmation)
            db.enableUserByEmail(testEmail)

            // Login to get token
            val loginResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(testEmail, testPassword))
            }

            assertEquals(HttpStatusCode.OK, loginResponse.status, "Login failed: ${loginResponse.bodyAsText()}")

            val authResponse = loginResponse.body<AuthResponse>()
            val token = authResponse.token!!

            // Get the workspace ID by querying for workspaces (requires admin key)
            val workspaceResponse = client.get("/api/workspace/user/email/$testEmail") {
                contentType(ContentType.Application.Json)
                headers {
                    append("X-Admin-Key", adminKey)
                }
            }

            assertTrue(
                workspaceResponse.status.isSuccess(),
                "Workspace query failed: ${workspaceResponse.status} - ${workspaceResponse.bodyAsText()}"
            )

            val workspaces = workspaceResponse.body<List<WorkspaceApi>>()
            assertTrue(workspaces.isNotEmpty(), "No workspaces found for user")
            val workspaceId = workspaces.first().id

            // Ensure user is FREE tier
            val user = db.getUserByEmail(testEmail)!!
            assertEquals(Tier.FREE, user.tier, "User should be FREE tier by default")

            // Try to send documents (premium endpoint)
            val documentApi = DocumentApi(
                id = "testDoc_${Random.nextInt()}",
                title = "Test Document",
                workspaceId = workspaceId,
                parentId = "root",
                isLocked = false,
                createdAt = 1000L,
                lastUpdatedAt = 2000L,
                lastSyncedAt = 0L
            )

            val sendDocResponse = client.post("/api/docs/workspace/document") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(SendDocumentsRequest(listOf(documentApi), workspaceId))
            }

            assertEquals(
                HttpStatusCode.PaymentRequired,
                sendDocResponse.status,
                "Expected 402 Payment Required for FREE tier user, got: ${sendDocResponse.status}"
            )
        } finally {
            db.deleteUserByEmail(testEmail)
        }
    }

    @Test
    fun `PREMIUM tier user should be able to send documents successfully`() = testApplication {
        val testEmail = generateTestEmail()

        application {
            module(db, debugMode = false, adminKey = adminKey)
        }

        try {
            val client = defaultClient()

            // Register a new user
            val registerResponse = client.post("/api/auth/register") {
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

            assertTrue(registerResponse.status.isSuccess(), "Registration failed: ${registerResponse.bodyAsText()}")

            // Enable the user and upgrade to PREMIUM
            db.enableUserByEmail(testEmail)
            val user = db.getUserByEmail(testEmail)!!
            db.updateUserTier(user.id, Tier.PREMIUM)

            // Verify tier was updated
            val updatedUser = db.getUserByEmail(testEmail)!!
            assertEquals(Tier.PREMIUM, updatedUser.tier, "User tier should be upgraded to PREMIUM")

            // Login to get token
            val loginResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(testEmail, testPassword))
            }

            assertEquals(HttpStatusCode.OK, loginResponse.status, "Login failed: ${loginResponse.bodyAsText()}")

            val authResponse = loginResponse.body<AuthResponse>()
            val token = authResponse.token!!

            // Get the workspace ID
            val workspaceResponse = client.get("/api/workspace/user/email/$testEmail") {
                contentType(ContentType.Application.Json)
                headers {
                    append("X-Admin-Key", adminKey)
                }
            }

            assertTrue(workspaceResponse.status.isSuccess(), "Workspace query failed: ${workspaceResponse.bodyAsText()}")
            val workspaceId = workspaceResponse.body<List<WorkspaceApi>>().first().id

            // Send documents (premium endpoint)
            val documentApi = DocumentApi(
                id = "testDoc_${Random.nextInt()}",
                title = "Test Document",
                workspaceId = workspaceId,
                parentId = "root",
                isLocked = false,
                createdAt = 1000L,
                lastUpdatedAt = 2000L,
                lastSyncedAt = 0L
            )

            val sendDocResponse = client.post("/api/docs/workspace/document") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(SendDocumentsRequest(listOf(documentApi), workspaceId))
            }

            assertEquals(
                HttpStatusCode.OK,
                sendDocResponse.status,
                "PREMIUM user should be able to send documents: ${sendDocResponse.bodyAsText()}"
            )
        } finally {
            db.deleteUserByEmail(testEmail)
        }
    }

    @Test
    fun `FREE tier user should receive 402 when requesting workspace diff`() = testApplication {
        val testEmail = generateTestEmail()

        application {
            module(db, debugMode = false, adminKey = adminKey)
        }

        try {
            val client = defaultClient()

            // Register, enable, and login
            val registerResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("Test Workspace", "Test User", testEmail, testPassword))
            }
            assertTrue(registerResponse.status.isSuccess(), "Registration failed: ${registerResponse.bodyAsText()}")

            db.enableUserByEmail(testEmail)

            val loginResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(testEmail, testPassword))
            }
            assertEquals(HttpStatusCode.OK, loginResponse.status, "Login failed: ${loginResponse.bodyAsText()}")
            val token = loginResponse.body<AuthResponse>().token!!

            // Get workspace ID
            val workspaceResponse = client.get("/api/workspace/user/email/$testEmail") {
                contentType(ContentType.Application.Json)
                headers { append("X-Admin-Key", adminKey) }
            }
            assertTrue(workspaceResponse.status.isSuccess(), "Workspace query failed: ${workspaceResponse.bodyAsText()}")
            val workspaceId = workspaceResponse.body<List<WorkspaceApi>>().first().id

            // Try workspace diff (premium endpoint)
            val diffResponse = client.post("/api/docs/workspace/diff") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(WorkspaceDiffRequest(workspaceId = workspaceId, lastSync = 0))
            }

            assertEquals(
                HttpStatusCode.PaymentRequired,
                diffResponse.status,
                "Expected 402 Payment Required, got: ${diffResponse.status}"
            )
        } finally {
            db.deleteUserByEmail(testEmail)
        }
    }

    @Test
    fun `PREMIUM tier user should be able to get workspace diff`() = testApplication {
        val testEmail = generateTestEmail()

        application {
            module(db, debugMode = false, adminKey = adminKey)
        }

        try {
            val client = defaultClient()

            // Register and enable
            val registerResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("Test Workspace", "Test User", testEmail, testPassword))
            }
            assertTrue(registerResponse.status.isSuccess(), "Registration failed: ${registerResponse.bodyAsText()}")

            db.enableUserByEmail(testEmail)

            // Upgrade to PREMIUM
            val user = db.getUserByEmail(testEmail)!!
            db.updateUserTier(user.id, Tier.PREMIUM)

            // Login
            val loginResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(testEmail, testPassword))
            }
            assertEquals(HttpStatusCode.OK, loginResponse.status, "Login failed: ${loginResponse.bodyAsText()}")
            val token = loginResponse.body<AuthResponse>().token!!

            // Get workspace ID
            val workspaceResponse = client.get("/api/workspace/user/email/$testEmail") {
                contentType(ContentType.Application.Json)
                headers { append("X-Admin-Key", adminKey) }
            }
            assertTrue(workspaceResponse.status.isSuccess(), "Workspace query failed: ${workspaceResponse.bodyAsText()}")
            val workspaceId = workspaceResponse.body<List<WorkspaceApi>>().first().id

            // Get workspace diff (premium endpoint)
            val diffResponse = client.post("/api/docs/workspace/diff") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(WorkspaceDiffRequest(workspaceId = workspaceId, lastSync = 0))
            }

            assertEquals(
                HttpStatusCode.OK,
                diffResponse.status,
                "PREMIUM user should be able to get workspace diff: ${diffResponse.bodyAsText()}"
            )
        } finally {
            db.deleteUserByEmail(testEmail)
        }
    }

    @Test
    fun `user not in workspace should receive 403 Forbidden even if PREMIUM`() = testApplication {
        val testEmail = generateTestEmail()

        application {
            module(db, debugMode = false, adminKey = adminKey)
        }

        try {
            val client = defaultClient()

            // Register, enable, and upgrade to PREMIUM
            val registerResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("Test Workspace", "Test User", testEmail, testPassword))
            }
            assertTrue(registerResponse.status.isSuccess(), "Registration failed: ${registerResponse.bodyAsText()}")

            db.enableUserByEmail(testEmail)
            val user = db.getUserByEmail(testEmail)!!
            db.updateUserTier(user.id, Tier.PREMIUM)

            // Login
            val loginResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(testEmail, testPassword))
            }
            assertEquals(HttpStatusCode.OK, loginResponse.status, "Login failed: ${loginResponse.bodyAsText()}")
            val token = loginResponse.body<AuthResponse>().token!!

            // Try to access a different workspace that user is not a member of
            val differentWorkspaceId = "other_workspace_${Random.nextInt()}"

            val documentApi = DocumentApi(
                id = "testDoc_${Random.nextInt()}",
                title = "Test Document",
                workspaceId = differentWorkspaceId,
                parentId = "root",
                isLocked = false,
                createdAt = 1000L,
                lastUpdatedAt = 2000L,
                lastSyncedAt = 0L
            )

            val sendDocResponse = client.post("/api/docs/workspace/document") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(SendDocumentsRequest(listOf(documentApi), differentWorkspaceId))
            }

            assertEquals(
                HttpStatusCode.Forbidden,
                sendDocResponse.status,
                "Expected 403 Forbidden for non-member, got: ${sendDocResponse.status}"
            )
        } finally {
            db.deleteUserByEmail(testEmail)
        }
    }

    @Test
    fun `debugMode should bypass tier check for development purposes`() = testApplication {
        application {
            // Enable debug mode - should bypass tier checks
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val workspaceId = "debug_workspace_${Random.nextInt()}"

        // In debug mode, no auth is required and tier checks are bypassed
        val documentApi = DocumentApi(
            id = "testDoc_${Random.nextInt()}",
            title = "Test Document",
            workspaceId = workspaceId,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val sendDocResponse = client.post("/api/docs/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi), workspaceId))
        }

        assertEquals(
            HttpStatusCode.OK,
            sendDocResponse.status,
            "Debug mode should bypass tier checks: ${sendDocResponse.bodyAsText()}"
        )
    }
}
