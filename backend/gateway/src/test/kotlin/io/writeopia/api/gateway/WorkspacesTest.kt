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
import io.writeopia.app.requests.AddUserToWorkspaceRequest
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
        val email1 = "rolechange1_${Random.nextInt(10000)}@test.com"
        val email2 = "rolechange2_${Random.nextInt(10000)}@test.com"
        val workspaceName1 = "workspace name"

        // Register first user (creates workspace with user as ADMIN)
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = workspaceName1,
                    name = "Name",
                    email = email1,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertEquals(response.status, HttpStatusCode.Created)

        // Register second user
        val response2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "other workspace",
                    name = "Name 2",
                    email = email2,
                    password = "lasjbdalsdq08w9y&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response2.status)

        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email1") {
            contentType(ContentType.Application.Json)
        }

        val workspace1 = getWorkspaceResponse.body<List<WorkspaceApi>>().first()
        assertEquals(workspaceName1, workspace1.name)

        // Add second user to the workspace as ADMIN (so we have 2 admins)
        val addUserResponse = client.post("/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(
                AddUserToWorkspaceRequest(
                    email = email2,
                    workspaceId = workspace1.id,
                    role = "ADMIN"
                )
            )
        }

        assertTrue(addUserResponse.status.isSuccess())

        val getUserInWorkspace = client.get(
            "/api/workspace/${workspace1.id}/user/$email1"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        // Now we can change the role since there's another admin
        val newRole = "EDITOR"
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
            "/api/workspace/${workspace1.id}/user/$email1"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        assertEquals(newRole, getUserInWorkspace2.role)

        // Clean up
        db.deleteUserByEmail(email1)
        db.deleteUserByEmail(email2)
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

    @Test
    fun `it should NOT be possible to change the last admin to editor`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = "lastadmin_${Random.nextInt(10000)}@test.com"
        val workspaceName = "workspace_lastadmin_test"

        // Register a user (creates workspace with user as ADMIN)
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = workspaceName,
                    name = "Admin User",
                    email = email,
                    password = "testpassword123&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)

        // Get the workspace
        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        val workspace = getWorkspaceResponse.body<List<WorkspaceApi>>().first()

        // Get user in workspace to get user ID
        val getUserInWorkspace = client.get(
            "/api/workspace/${workspace.id}/user/$email"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        // Verify user is ADMIN
        assertEquals("ADMIN", getUserInWorkspace.role)

        // Try to change the only admin to EDITOR - should fail
        val roleChangeResponse = client.put("/api/workspace/role") {
            contentType(ContentType.Application.Json)
            setBody(
                WorkspaceRoleChangeRequest(
                    workspaceId = workspace.id,
                    userId = getUserInWorkspace.id,
                    newRole = "EDITOR"
                )
            )
        }

        // Should return 409 Conflict
        assertEquals(HttpStatusCode.Conflict, roleChangeResponse.status)

        // Verify user is still ADMIN
        val getUserInWorkspace2 = client.get(
            "/api/workspace/${workspace.id}/user/$email"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        assertEquals("ADMIN", getUserInWorkspace2.role)

        // Clean up
        db.deleteUserByEmail(email)
    }

    @Test
    fun `it should be possible to change an admin to editor when there are multiple admins`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email1 = "multiadmin1_${Random.nextInt(10000)}@test.com"
        val email2 = "multiadmin2_${Random.nextInt(10000)}@test.com"
        val workspaceName = "workspace_multiadmin_test"

        // Register first user (creates workspace with user as ADMIN)
        val response1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = workspaceName,
                    name = "Admin User 1",
                    email = email1,
                    password = "testpassword123&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response1.status)

        // Register second user
        val response2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "other workspace",
                    name = "Admin User 2",
                    email = email2,
                    password = "testpassword123&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response2.status)

        // Get the workspace of user 1
        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email1") {
            contentType(ContentType.Application.Json)
        }

        val workspace = getWorkspaceResponse.body<List<WorkspaceApi>>().first()

        // Add second user to the workspace as ADMIN
        val addUserResponse = client.post("/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(
                AddUserToWorkspaceRequest(
                    email = email2,
                    workspaceId = workspace.id,
                    role = "ADMIN"
                )
            )
        }

        assertTrue(addUserResponse.status.isSuccess())

        // Get first user in workspace to get user ID
        val getUser1InWorkspace = client.get(
            "/api/workspace/${workspace.id}/user/$email1"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        // Verify user1 is ADMIN
        assertEquals("ADMIN", getUser1InWorkspace.role)

        // Change first admin to EDITOR - should succeed because there's another admin
        val roleChangeResponse = client.put("/api/workspace/role") {
            contentType(ContentType.Application.Json)
            setBody(
                WorkspaceRoleChangeRequest(
                    workspaceId = workspace.id,
                    userId = getUser1InWorkspace.id,
                    newRole = "EDITOR"
                )
            )
        }

        // Should succeed
        assertTrue(roleChangeResponse.status.isSuccess())

        // Verify user1 is now EDITOR
        val getUser1InWorkspace2 = client.get(
            "/api/workspace/${workspace.id}/user/$email1"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        assertEquals("EDITOR", getUser1InWorkspace2.role)

        // Clean up
        db.deleteUserByEmail(email1)
        db.deleteUserByEmail(email2)
    }

    @Test
    fun `it should be possible to change an editor to admin`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email1 = "editoradmin1_${Random.nextInt(10000)}@test.com"
        val email2 = "editoradmin2_${Random.nextInt(10000)}@test.com"
        val workspaceName = "workspace_editoradmin_test"

        // Register first user (creates workspace with user as ADMIN)
        val response1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = workspaceName,
                    name = "Admin User",
                    email = email1,
                    password = "testpassword123&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response1.status)

        // Register second user
        val response2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "other workspace",
                    name = "Editor User",
                    email = email2,
                    password = "testpassword123&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response2.status)

        // Get the workspace of user 1
        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email1") {
            contentType(ContentType.Application.Json)
        }

        val workspace = getWorkspaceResponse.body<List<WorkspaceApi>>().first()

        // Add second user to the workspace as EDITOR
        val addUserResponse = client.post("/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(
                AddUserToWorkspaceRequest(
                    email = email2,
                    workspaceId = workspace.id,
                    role = "EDITOR"
                )
            )
        }

        assertTrue(addUserResponse.status.isSuccess())

        // Get second user in workspace to get user ID
        val getUser2InWorkspace = client.get(
            "/api/workspace/${workspace.id}/user/$email2"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        // Verify user2 is EDITOR
        assertEquals("EDITOR", getUser2InWorkspace.role)

        // Change editor to ADMIN - should succeed
        val roleChangeResponse = client.put("/api/workspace/role") {
            contentType(ContentType.Application.Json)
            setBody(
                WorkspaceRoleChangeRequest(
                    workspaceId = workspace.id,
                    userId = getUser2InWorkspace.id,
                    newRole = "ADMIN"
                )
            )
        }

        // Should succeed
        assertTrue(roleChangeResponse.status.isSuccess())

        // Verify user2 is now ADMIN
        val getUser2InWorkspace2 = client.get(
            "/api/workspace/${workspace.id}/user/$email2"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        assertEquals("ADMIN", getUser2InWorkspace2.role)

        // Clean up
        db.deleteUserByEmail(email1)
        db.deleteUserByEmail(email2)
    }

    @Test
    fun `changing the last admin to admin again should succeed`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = "sameadmin_${Random.nextInt(10000)}@test.com"
        val workspaceName = "workspace_sameadmin_test"

        // Register a user (creates workspace with user as ADMIN)
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = workspaceName,
                    name = "Admin User",
                    email = email,
                    password = "testpassword123&",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)

        // Get the workspace
        val getWorkspaceResponse = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        val workspace = getWorkspaceResponse.body<List<WorkspaceApi>>().first()

        // Get user in workspace to get user ID
        val getUserInWorkspace = client.get(
            "/api/workspace/${workspace.id}/user/$email"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        // Verify user is ADMIN
        assertEquals("ADMIN", getUserInWorkspace.role)

        // Change admin to ADMIN again - should succeed (no actual change)
        val roleChangeResponse = client.put("/api/workspace/role") {
            contentType(ContentType.Application.Json)
            setBody(
                WorkspaceRoleChangeRequest(
                    workspaceId = workspace.id,
                    userId = getUserInWorkspace.id,
                    newRole = "ADMIN"
                )
            )
        }

        // Should succeed
        assertTrue(roleChangeResponse.status.isSuccess())

        // Verify user is still ADMIN
        val getUserInWorkspace2 = client.get(
            "/api/workspace/${workspace.id}/user/$email"
        ) {
            contentType(ContentType.Application.Json)
        }.body<WorkspaceUserApi>()

        assertEquals("ADMIN", getUserInWorkspace2.role)

        // Clean up
        db.deleteUserByEmail(email)
    }
}
