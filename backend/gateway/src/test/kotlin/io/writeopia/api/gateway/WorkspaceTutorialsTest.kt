package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.repository.deleteUserByEmail
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.app.requests.CreateWorkspaceRequest
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceTutorialsTest {
    private val db = configurePersistence()

    private val testEmails = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        testEmails.clear()
    }

    @AfterTest
    fun tearDown() {
        testEmails.forEach { email ->
            db.deleteUserByEmail(email)
        }
    }

    @Test
    fun `tutorials should be created when a new workspace is created`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = "tutorials_create_${Random.nextInt(10000)}@test.com"
        val password = "testpassword123&"
        testEmails.add(email)

        // Register a user first (this creates an initial workspace without tutorials)
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "Initial Workspace",
                    name = "Test User",
                    email = email,
                    password = password,
                )
            )
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status)

        // Login to get access token
        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val authResponse = loginResponse.body<AuthResponse>()
        val accessToken = authResponse.accessToken!!

        // Create a NEW workspace - this should initialize tutorials
        val newWorkspaceName = "New Workspace With Tutorials"
        val createWorkspaceResponse = client.post("/api/workspace/create") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(CreateWorkspaceRequest(newWorkspaceName))
        }

        assertEquals(HttpStatusCode.Created, createWorkspaceResponse.status)

        // Get all workspaces for the user (admin endpoint)
        val getWorkspacesResponse = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, getWorkspacesResponse.status)
        val workspaces = getWorkspacesResponse.body<List<WorkspaceApi>>()

        // Find the newly created workspace
        val newWorkspace = workspaces.find { it.name == newWorkspaceName }
        assertTrue(newWorkspace != null, "New workspace should exist. Found workspaces: ${workspaces.map { it.name }}")

        // The new workspace should have tutorial documents (5 tutorials)
        // Tutorials: welcomeTutorial, aiTutorial, savingNotesTutorial, commandsTutorial, videoTutorial
        assertEquals(5, newWorkspace.documentCount, "New workspace should have 5 tutorial documents")
    }

    @Test
    fun `tutorials should only be created once per workspace per user`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = "tutorials_idempotent_${Random.nextInt(10000)}@test.com"
        val password = "testpassword123&"
        testEmails.add(email)

        // Register a user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "Initial Workspace",
                    name = "Test User",
                    email = email,
                    password = password,
                )
            )
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status)

        // Login to get access token
        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val authResponse = loginResponse.body<AuthResponse>()
        val accessToken = authResponse.accessToken!!

        // Create a new workspace
        val workspaceName = "Workspace For Idempotent Test"
        val createResponse1 = client.post("/api/workspace/create") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(CreateWorkspaceRequest(workspaceName))
        }

        assertEquals(HttpStatusCode.Created, createResponse1.status)

        // Get the workspace and check document count
        val getWorkspacesResponse1 = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        val workspaces1 = getWorkspacesResponse1.body<List<WorkspaceApi>>()
        val workspace1 = workspaces1.find { it.name == workspaceName }
        assertTrue(workspace1 != null, "Workspace should exist")
        val initialDocCount = workspace1.documentCount

        // Manually call the tutorials initialization endpoint again
        // This should not create duplicate tutorials
        val initTutorialsResponse = client.post("/api/docs/workspace/${workspace1.id}/tutorials/initialize") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, initTutorialsResponse.status)

        // Get the workspace again and verify document count hasn't changed
        val getWorkspacesResponse2 = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        val workspaces2 = getWorkspacesResponse2.body<List<WorkspaceApi>>()
        val workspace2 = workspaces2.find { it.name == workspaceName }
        assertTrue(workspace2 != null, "Workspace should still exist")

        assertEquals(
            initialDocCount,
            workspace2.documentCount,
            "Document count should remain the same after re-initialization"
        )
    }

    @Test
    fun `multiple workspaces created by same user should each have their own tutorials`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val email = "tutorials_multiple_${Random.nextInt(10000)}@test.com"
        val password = "testpassword123&"
        testEmails.add(email)

        // Register a user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    workspaceName = "Initial Workspace",
                    name = "Test User",
                    email = email,
                    password = password,
                )
            )
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status)

        // Login to get access token
        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val authResponse = loginResponse.body<AuthResponse>()
        val accessToken = authResponse.accessToken!!

        // Create first new workspace
        val workspace1Name = "First New Workspace"
        val createResponse1 = client.post("/api/workspace/create") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(CreateWorkspaceRequest(workspace1Name))
        }

        assertEquals(HttpStatusCode.Created, createResponse1.status)

        // Create second new workspace
        val workspace2Name = "Second New Workspace"
        val createResponse2 = client.post("/api/workspace/create") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(CreateWorkspaceRequest(workspace2Name))
        }

        assertEquals(HttpStatusCode.Created, createResponse2.status)

        // Get all workspaces
        val getWorkspacesResponse = client.get("/api/workspace/user/email/$email") {
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, getWorkspacesResponse.status)
        val workspaces = getWorkspacesResponse.body<List<WorkspaceApi>>()

        // Find both new workspaces
        val newWorkspace1 = workspaces.find { it.name == workspace1Name }
        val newWorkspace2 = workspaces.find { it.name == workspace2Name }

        assertTrue(newWorkspace1 != null, "First new workspace should exist")
        assertTrue(newWorkspace2 != null, "Second new workspace should exist")

        // Both workspaces should have 5 tutorials each
        assertEquals(5, newWorkspace1.documentCount, "First workspace should have 5 tutorials")
        assertEquals(5, newWorkspace2.documentCount, "Second workspace should have 5 tutorials")

        // They should have different IDs
        assertTrue(
            newWorkspace1.id != newWorkspace2.id,
            "Workspaces should have different IDs"
        )
    }
}
