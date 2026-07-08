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
import io.writeopia.api.documents.documents.repository.deleteDocumentById
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.app.dto.WorkspaceUserApi
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
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

        val response = client.post("/api/auth/register") {
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

        val response = client.post("/api/auth/register") {
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

    @Test
    fun `workspace should return document count`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = "doccount_${Random.nextInt(10000)}@test.com"
        val workspaceName = "workspace_doccount_test"

        // Register a user (which creates a workspace)
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = workspaceName,
                    name = "Test User",
                    email = email,
                    password = "testpassword123&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status)

        // Get the workspace
        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, getWorkspaceResponse.status)
        val workspaces = getWorkspaceResponse.body<List<WorkspaceApi>>()
        assertTrue(workspaces.isNotEmpty())

        val workspace = workspaces.first()

        // Initially, workspace should have 0 documents
        assertEquals(0, workspace.documentCount)

        // Create some documents in the workspace
        val document1 = DocumentApi(
            id = "doccount_test_1_${Random.nextInt()}",
            title = "Test Document 1",
            workspaceId = workspace.id,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val document2 = DocumentApi(
            id = "doccount_test_2_${Random.nextInt()}",
            title = "Test Document 2",
            workspaceId = workspace.id,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val document3 = DocumentApi(
            id = "doccount_test_3_${Random.nextInt()}",
            title = "Test Document 3",
            workspaceId = workspace.id,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val createDocsResponse = client.post("/api/docs/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(document1, document2, document3), workspace.id))
        }

        assertEquals(HttpStatusCode.OK, createDocsResponse.status)

        // Get the workspace again and verify document count
        val getWorkspaceResponse2 = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, getWorkspaceResponse2.status)
        val workspaces2 = getWorkspaceResponse2.body<List<WorkspaceApi>>()
        val workspace2 = workspaces2.first()

        // Now workspace should have 3 documents
        assertEquals(3, workspace2.documentCount)

        // Clean up
        db.deleteDocumentById(document1.id)
        db.deleteDocumentById(document2.id)
        db.deleteDocumentById(document3.id)
        db.deleteUserByEmail(email)
    }
}
