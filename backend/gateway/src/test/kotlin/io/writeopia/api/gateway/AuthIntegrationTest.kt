package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.models.AddUserToWorkspaceRequest
import io.writeopia.api.core.auth.models.ManageUserRequest
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.models.Workspace
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.data.auth.ResetPasswordRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
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

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                    workspace = ""
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)
    }

    @Test
    fun `it should not be possible create 2 users with the same email`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                    workspace = ""
                )
            )
        }

        val response1 = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                    workspace = ""
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)
        assertEquals(response1.status, HttpStatusCode.Conflict)
    }

    @Test
    fun `it should be possible to delete your account, if your logged in`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val password = "lasjbdalsdq08w9y&"

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = password,
                    workspace = ""
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)

        val response1 = client.post("api/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("email@gmail.com", password))
        }

        assertEquals(response1.status, HttpStatusCode.OK)

        val token = response1.body<AuthResponse>().token!!

        val response2 = client.delete("api/account") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(response2.status, HttpStatusCode.OK)
    }

    @Test
    fun `it should be possible to delete your account, if don't have the right token`() =
        testApplication {
            application {
                module(db, debugMode = true)
            }

            val response2 = client.delete("api/account") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer asdasdasd")
            }

            assertEquals(response2.status, HttpStatusCode.Unauthorized)
        }

    @Test
    fun `it should be possible to reset my password`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val password = "lasjbdalsdq08w9y&"

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = password,
                    workspace = ""
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)

        val response1 = client.post("api/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("email@gmail.com", password))
        }

        assertEquals(response1.status, HttpStatusCode.OK)

        val token = response1.body<AuthResponse>().token!!

        val response2 = client.put("api/password/reset") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(ResetPasswordRequest(newPassword = "newpassword"))
        }

        assertEquals(response2.status, HttpStatusCode.OK)

        val response3 = client.get("api/user/current") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(response3.status, HttpStatusCode.OK)
    }

    @Test
    fun `it should be possible enable a user`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val response = client.post("admin/enable-user") {
            contentType(ContentType.Application.Json)
            setBody(ManageUserRequest(email = "lehen01@gmail.com"))
            headers {
                this.append("X-Admin-Key", "somekey")
            }
        }

        assertEquals(response.status, HttpStatusCode.OK)
    }

    @Test
    fun `it should be possible disable a user`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val response = client.post("admin/disable-user") {
            contentType(ContentType.Application.Json)
            setBody(ManageUserRequest(email = "lehen01@gmail.com"))
            headers {
                this.append("X-Admin-Key", "somekey")
            }
        }

        assertEquals(response.status, HttpStatusCode.OK)
    }

    @Test
    fun `when registering, it should return a workspace`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                    workspace = ""
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertNotNull(response.body<AuthResponse>().workspace)
    }

    @Test
    fun `when login in, it should return a workspace`() = testApplication {
        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "Name",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                    workspace = ""
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertNotNull(response.body<AuthResponse>().workspace)

        val response1 = client.post("api/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("email@gmail.com", "lasjbdalsdq08w9y&"))
        }

        assertEquals(HttpStatusCode.OK, response1.status)
        assertNotNull(response.body<AuthResponse>().workspace)
    }

    @Test
    fun `it should be possible to add a user to a workspace`() = testApplication {
        //Todo: Create a test to add a user to a workspace

        application {
            module(db, debugMode = true, adminKey = "somekey")
        }

        val client = defaultClient()

        val response1 = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "User1",
                    email = "email@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                    workspace = "Workspace1"
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response1.status)

        val response2 = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = "User2",
                    email = "email2@gmail.com",
                    password = "lasjbdalsdq08w9y&",
                    workspace = "Workspace2"
                )
            )
        }

        assertTrue { response2.status.isSuccess() }

        val getWorkspaceResponse = client.get("/admin/workspace/{email@gmail.com}") {
            contentType(ContentType.Application.Json)
        }

        val workspaceOfUser1 = getWorkspaceResponse.body<List<Workspace>>().first()

        val addUserToWorkspace = client.post("/admin/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(
                AddUserToWorkspaceRequest(
                    email = "email2@gmail.com",
                    workspaceId = workspaceOfUser1.id,
                    role = "user"
                )
            )
        }

        // Todo: Finish. Check that User2 is in the Workspace1
    }

}
