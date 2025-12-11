package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.documents.documents.repository.deleteDocumentById
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.FolderApi
import io.writeopia.sdk.serialization.data.StoryStepApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.json.SendFoldersRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffResponse
import kotlin.time.Clock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentationIntegrationTests {

    private val db = configurePersistence()

    @Test
    fun `it should be possible to save and query document by id`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspace = Random.nextInt().toString()

        val documentApiList = listOf(
            DocumentApi(
                id = "testiaskkakakaka",
                title = "Test Note",
                workspaceId = workspace,
                parentId = "parentIdddd",
                isLocked = false,
                createdAt = 1000L,
                lastUpdatedAt = 2000L,
                lastSyncedAt = 0L
            )
        )

        val response = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documentApiList, workspace))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 =
            client.get("/api/workspace/$workspace/document/${documentApiList.first().id}")
        assertEquals(HttpStatusCode.OK, response1.status)

        val actual = response1.body<DocumentApi>().copy(lastSyncedAt = 0L)

        assertEquals(
            documentApiList.first(),
            actual
        )

        documentApiList.forEach { documentApi ->
            db.deleteDocumentById(documentApi.id)
        }
    }

    @Test
    fun `it should be possible to change the name of a workspace`() = testApplication {

    }

    @Test
    fun `it should be possible to save and query folder by id`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val folderApiList = listOf(
            FolderApi(
                id = "testiaskkakakaka",
                title = "Test Note",
                parentId = "parentIdddd",
                createdAt = Clock.System.now(),
                lastUpdatedAt = Clock.System.now(),
                workspaceId = "",
                itemCount = 0L,
            )
        )

        val response = client.post("/api/workspace/folder") {
            contentType(ContentType.Application.Json)
            setBody(SendFoldersRequest(folderApiList, "someSpace"))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.get("/api/workspace/someSpace/folder/${folderApiList.first().id}")
        val actual = response1.body<FolderApi>()

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(folderApiList.first().id, actual.id)

        folderApiList.forEach { documentApi ->
            db.deleteDocumentById(documentApi.id)
        }
    }

    @Test
    fun `it should be possible to save and query documents by parent id`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val workspace = Random.nextInt().toString()

        val documentApiList = listOf(
            DocumentApi(
                id = "testiaskkakakaka",
                title = "Test Note",
                workspaceId = workspace,
                parentId = "parentIdddd",
                isLocked = false,
                createdAt = 1000L,
                lastUpdatedAt = 2000L,
                lastSyncedAt = 2000
            )
        )

        val response = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documentApiList, workspace))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.get(
            "/api/workspace/workspace/document/parent/${documentApiList.first().parentId}"
        )

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(listOf(documentApiList.first()), response1.body())

        documentApiList.forEach { documentApi ->
            db.deleteDocumentById(documentApi.id)
        }
    }

    @Test
    fun `it should be possible to save and query ids by parent id`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val workspace = Random.nextInt().toString()
        val documentApi = DocumentApi(
            id = "testias",
            title = "Test Note",
            workspaceId = workspace,
            parentId = "parentId",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L
        )

        val response = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi), workspace))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 =
            client.get("/api/workspace/workspace/document/parent/${documentApi.parentId}")

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(
            listOf(documentApi).map { it.id },
            response1.body<List<DocumentApi>>().map { it.id }
        )

        db.deleteDocumentById(documentApi.id)
    }

    @Test
    fun `it should be possible to get diff of folders`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspace = Random.nextInt().toString()

        val content: Map<Int, StoryStepApi> = mapOf(
            0 to StoryStep(type = StoryTypes.TEXT.type, text = "message1"),
            1 to StoryStep(type = StoryTypes.TEXT.type, text = "message2"),
            2 to StoryStep(type = StoryTypes.TEXT.type, text = "message3"),
            3 to StoryStep(type = StoryTypes.TEXT.type, text = "message4"),
        ).mapValues { (position, step) ->
            step.toApi(position)
        }

        val documentApi = DocumentApi(
            id = "testias",
            title = "Test Note",
            workspaceId = workspace,
            parentId = "parentId",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 4000,
            content = content.values.toList()
        )

        val documentApi2 = documentApi.copy(id = "testias2", lastUpdatedAt = 4000L)

        val response = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi), workspace))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi2), workspace))
        }

        assertEquals(HttpStatusCode.OK, response1.status)

        val request = FolderDiffRequest(
            folderId = "parentId",
            workspaceId = workspace,
            lastFolderSync = 3000L
        )

        val response2 = client.post("/api/workspace/document/folder/diff") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.OK, response2.status)

        val diff = response2.body<List<DocumentApi>>()

        assertEquals(listOf(documentApi2), diff)

        db.deleteDocumentById(documentApi.id)
        db.deleteDocumentById(documentApi2.id)
    }

    @Test
    fun `it should be possible to get diff of workspace`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val workspaceId = Random.nextInt().toString()

        val documentApi = DocumentApi(
            id = "testias",
            title = "Test Note",
            workspaceId = workspaceId,
            parentId = "parentId",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 4000
        )

        val documentApi2 = documentApi.copy(id = "testias2", lastUpdatedAt = 4000L)

        val response = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi), workspaceId))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi2), workspaceId))
        }

        assertEquals(HttpStatusCode.OK, response1.status)

        val request = WorkspaceDiffRequest(
            workspaceId = workspaceId,
            lastSync = 0
        )

        val response2 = client.post("/api/workspace/diff") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.OK, response2.status)
        assertEquals(
            listOf(documentApi, documentApi2).map { it.id },
            response2.body<WorkspaceDiffResponse>().documents.map { it.id }
        )

        db.deleteDocumentById(documentApi.id)
        db.deleteDocumentById(documentApi2.id)
    }
}
