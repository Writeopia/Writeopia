package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.writeopia.app.requests.AddUserToWorkspaceRequest
import io.writeopia.api.core.auth.models.ManageUserRequest
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.data.auth.RegisterResponse
import io.writeopia.sdk.serialization.data.auth.ResetPasswordRequest
import io.writeopia.sdk.serialization.json.writeopiaJson
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthIntegrationTest {

    private val db = configurePersistence()

    @BeforeTest
    fun setUp() {
        db.deleteUserByEmail("email@gmail.com")
    }

    @AfterTest
    fun tearDown() {
        db.deleteUserByEmail("email@gmail.com")
    }

    @Test
    fun `it should be possible to register an user`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `it should not be possible create 2 users with the same email`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = Random.nextInt().toString()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = "Name",
                    email = email,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        val response1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = "Name",
                    email = email,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(HttpStatusCode.Conflict, response1.status)
    }

    @Test
    fun `it should be possible to delete your account, if your logged in`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val password = "lasjbdalsdq08w9y&"

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = "Name",
                    email = "email@gmail.com",
                    password = password,
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val response1 = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("email@gmail.com", password))
        }

        assertEquals(HttpStatusCode.OK, response1.status)
        val authResponse = response1.body<AuthResponse>()
        val accessToken = authResponse.accessToken!!

        val response2 = client.delete("/api/auth/account") {
            contentType(ContentType.Application.Json)
            headers {
                append("X-Forwarded-Authorization", "Bearer $accessToken")
            }
        }

        assertEquals(HttpStatusCode.OK, response2.status)
    }

    @Test
    fun `it should be NOT possible to delete your account, if don't have the right token`() =
        testApplication {
            application {
                module(db, debugMode = false)
            }

            val client = defaultClient()

            val response2 = client.delete("/api/auth/account") {
                contentType(ContentType.Application.Json)
            }

            assertEquals(HttpStatusCode.Unauthorized, response2.status)
        }

    @Test
    fun `it should be possible to reset my password`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val password = "lasjbdalsdq08w9y&"

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = "Name",
                    email = "email@gmail.com",
                    password = password,
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val response1 = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("email@gmail.com", password))
        }

        assertEquals(HttpStatusCode.OK, response1.status)
        val authResponse = response1.body<AuthResponse>()
        val accessToken = authResponse.accessToken!!

        val response2 = client.put("/api/auth/password/reset") {
            contentType(ContentType.Application.Json)
            headers {
                append("X-Forwarded-Authorization", "Bearer $accessToken")
            }
            setBody(ResetPasswordRequest(newPassword = "newpassword"))
        }

        assertEquals(HttpStatusCode.OK, response2.status)

        val response3 = client.get("/api/auth/user/current") {
            contentType(ContentType.Application.Json)
            headers {
                append("X-Forwarded-Authorization", "Bearer $accessToken")
            }
        }

        assertEquals(HttpStatusCode.OK, response3.status)
    }

    @Test
    fun `it should be possible enable a user`() = testApplication {
        application {
            module(db, debugMode = false, adminKey = "somekey")
        }

        val client = defaultClient()

        val response = client.post("/api/auth/admin/enable-user") {
            contentType(ContentType.Application.Json)
            setBody(ManageUserRequest(email = "lehen01@gmail.com"))
            headers {
                this.append("X-Admin-Key", "somekey")
            }
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `it should be possible disable a user`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val response = client.post("/api/auth/admin/disable-user") {
            contentType(ContentType.Application.Json)
            setBody(ManageUserRequest(email = "lehen01@gmail.com"))
            headers {
                this.append("X-Admin-Key", "somekey")
            }
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `when registering, it should return a workspace`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertNotNull(response.body<RegisterResponse>().writeopiaUser)
    }

    @Test
    fun `when login in, it should return a workspace`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = createClientWithCookies()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertNotNull(response.body<RegisterResponse>().writeopiaUser)

        val response1 = client.post("/api/auth/login/web") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("email@gmail.com", "lasjbdalsdq08w9y&"))
        }

        assertEquals(HttpStatusCode.OK, response1.status)
        assertNotNull(response1.body<AuthResponse>().writeopiaUser)
    }

    @Test
    fun `it should be possible to add a user to a workspace`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val email1 = Random.nextInt().toString()

        val response1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = Random.nextInt().toString(),
                    email = email1,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertTrue { response1.status.isSuccess() }

        val email2 = Random.nextInt().toString()

        val response2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = Random.nextInt().toString(),
                    email = email2,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertTrue { response2.status.isSuccess() }

        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email1") {
            contentType(ContentType.Application.Json)
        }

        val workspaceOfUser1 = getWorkspaceResponse.body<List<WorkspaceApi>>().first()

        val addUserToWorkspace = client.post("/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(
                AddUserToWorkspaceRequest(
                    email = email2,
                    workspaceId = workspaceOfUser1.id,
                    role = "user"
                )
            )
        }

        assertTrue(addUserToWorkspace.status.isSuccess())

        val getWorkspaceResponse2 = client.get("/api/workspace/user/email/$email2") {
            contentType(ContentType.Application.Json)
        }

        val workspaceOfUser2 = getWorkspaceResponse2.body<List<WorkspaceApi>>()
        assertEquals(2, workspaceOfUser2.size)
        assertTrue { workspaceOfUser2.any { it.id == workspaceOfUser1.id } }
    }

    @Test
    fun `it should not be possible to add a user that is already in the workspace`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val email1 = Random.nextInt().toString()

        val response1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = Random.nextInt().toString(),
                    email = email1,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertTrue { response1.status.isSuccess() }

        val email2 = Random.nextInt().toString()

        val response2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = Random.nextInt().toString(),
                    email = email2,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertTrue { response2.status.isSuccess() }

        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email1") {
            contentType(ContentType.Application.Json)
        }

        val workspaceOfUser1 = getWorkspaceResponse.body<List<WorkspaceApi>>().first()

        // First add should succeed
        val addUserToWorkspace = client.post("/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(
                AddUserToWorkspaceRequest(
                    email = email2,
                    workspaceId = workspaceOfUser1.id,
                    role = "user"
                )
            )
        }

        assertTrue(addUserToWorkspace.status.isSuccess())

        // Second add of the same user should fail with Conflict
        val addUserToWorkspaceAgain = client.post("/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(
                AddUserToWorkspaceRequest(
                    email = email2,
                    workspaceId = workspaceOfUser1.id,
                    role = "user"
                )
            )
        }

        assertEquals(HttpStatusCode.Conflict, addUserToWorkspaceAgain.status)
    }

    @Test
    fun `it should be possible to remove a user to a workspace`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val email1 = Random.nextInt().toString()

        val response1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = Random.nextInt().toString(),
                    email = email1,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertTrue { response1.status.isSuccess() }

        val email2 = Random.nextInt().toString()

        val response2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "workspace name",
                    name = Random.nextInt().toString(),
                    email = email2,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertTrue { response2.status.isSuccess() }

        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email1") {
            contentType(ContentType.Application.Json)
        }

        val workspaceOfUser1 = getWorkspaceResponse.body<List<WorkspaceApi>>().first()

        val addUserToWorkspace = client.post("/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(
                AddUserToWorkspaceRequest(
                    email = email2,
                    workspaceId = workspaceOfUser1.id,
                    role = "user"
                )
            )
        }

        assertTrue(addUserToWorkspace.status.isSuccess())

        val getWorkspaceResponse2 = client.get("/api/workspace/user/email/$email2") {
            contentType(ContentType.Application.Json)
        }

        val workspaceOfUser2 = getWorkspaceResponse2.body<List<WorkspaceApi>>()
        assertEquals(2, workspaceOfUser2.size)
        assertTrue { workspaceOfUser2.any { it.id == workspaceOfUser1.id } }

        val deleteResponse =
            client.delete(
                "/api/workspace/${workspaceOfUser1.id}/user/email/$email2"
            )

        assertTrue(deleteResponse.status.isSuccess())

        val getWorkspaceResponse3 = client.get("/api/workspace/user/email/$email2") {
            contentType(ContentType.Application.Json)
        }

        val workspaceOfUser3 = getWorkspaceResponse3.body<List<WorkspaceApi>>()
        assertEquals(1, workspaceOfUser3.size)
    }
}

/**
 * Create a test client with cookie support for cookie-based authentication.
 */
private fun ApplicationTestBuilder.createClientWithCookies() = createClient {
    install(HttpCookies)
    install(ContentNegotiation) {
        json(json = writeopiaJson)
    }
}
