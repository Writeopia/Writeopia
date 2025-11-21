package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.app.dto.WorkspaceUserApi
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.request.WorkspaceNameChangeRequest
import io.writeopia.sdk.serialization.request.WorkspaceRoleChangeRequest
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspacesTest {
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
    fun `it should be possible to change the name of a workspace`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = Random.nextInt(10000).toString()
        val workspaceName1 = "workspace name"

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = workspaceName1,
                    name = "Name",
                    email = email,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)

        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        val workspace1 = getWorkspaceResponse.body<List<WorkspaceApi>>().first()
        assertEquals(workspaceName1, workspace1.name)

        val newName = "new name! asdas"
        val nameChangeResponse = client.put("/api/workspace/name") {
            contentType(ContentType.Application.Json)
            setBody(
                WorkspaceNameChangeRequest(
                    workspace1.id,
                    newName
                )
            )
        }

        assertTrue { nameChangeResponse.status.isSuccess() }
        val getWorkspaceResponse2 = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        val workspace2 = getWorkspaceResponse2.body<List<WorkspaceApi>>().first()
        assertEquals(newName, workspace2.name)
    }

    @Test
    fun `it should be possible to change the role of a user of a workspace`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = Random.nextInt(10000).toString()
        val workspaceName1 = "workspace name"

        val response = client.post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = workspaceName1,
                    name = "Name",
                    email = email,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)

        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        val workspace1 = getWorkspaceResponse.body<List<WorkspaceApi>>().first()
        assertEquals(workspaceName1, workspace1.name)

        val getUserInWorkspace = client.get(
            "/api/workspace/${workspace1.id}/user/$email"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        val newRole = "new role! asdas"
        val nameChangeResponse = client.put("/api/workspace/role") {
            contentType(ContentType.Application.Json)
            setBody(
                WorkspaceRoleChangeRequest(
                    workspaceId = workspace1.id,
                    userId = getUserInWorkspace.id,
                    newRole = newRole
                )
            )
        }

        assertTrue { nameChangeResponse.status.isSuccess() }
        val getUserInWorkspace2 = client.get(
            "/api/workspace/${workspace1.id}/user/$email"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        assertEquals(newRole, getUserInWorkspace2.role)
    }
}
