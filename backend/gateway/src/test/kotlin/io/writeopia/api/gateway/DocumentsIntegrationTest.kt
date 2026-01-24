@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.delete
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
import io.writeopia.sdk.serialization.request.CreateFolderRequest
import io.writeopia.sdk.serialization.request.DeleteDocumentsRequest
import io.writeopia.sdk.serialization.request.UpsertDocumentRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffRequest
import io.writeopia.sdk.serialization.response.FolderContentResponse
import io.writeopia.sdk.serialization.response.WorkspaceDiffResponse
import kotlin.time.Clock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

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

    @Test
    fun `it should be possible to get folder contents with folders and documents`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()
        val parentFolderId = "parentFolderId_${Random.nextInt()}"

        val childFolder1 = FolderApi(
            id = "childFolder1_${Random.nextInt()}",
            title = "Child Folder 1",
            parentId = parentFolderId,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = workspaceId,
            itemCount = 0L,
        )

        val childFolder2 = FolderApi(
            id = "childFolder2_${Random.nextInt()}",
            title = "Child Folder 2",
            parentId = parentFolderId,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = workspaceId,
            itemCount = 0L,
        )

        val document1 = DocumentApi(
            id = "document1_${Random.nextInt()}",
            title = "Document 1",
            workspaceId = workspaceId,
            parentId = parentFolderId,
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val document2 = DocumentApi(
            id = "document2_${Random.nextInt()}",
            title = "Document 2",
            workspaceId = workspaceId,
            parentId = parentFolderId,
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        // Save folders
        val folderResponse = client.post("/api/workspace/folder") {
            contentType(ContentType.Application.Json)
            setBody(SendFoldersRequest(listOf(childFolder1, childFolder2), workspaceId))
        }

        assertEquals(HttpStatusCode.OK, folderResponse.status)

        // Save documents
        val documentResponse = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(document1, document2), workspaceId))
        }

        assertEquals(HttpStatusCode.OK, documentResponse.status)

        // Get folder contents
        val contentsResponse = client.get("/api/workspace/$workspaceId/folder/$parentFolderId/contents")

        assertEquals(HttpStatusCode.OK, contentsResponse.status)

        val contents = contentsResponse.body<FolderContentResponse>()

        // Verify folders
        assertEquals(2, contents.folders.size)
        assertEquals(
            listOf(childFolder1.id, childFolder2.id).sorted(),
            contents.folders.map { it.id }.sorted()
        )

        // Verify documents
        assertEquals(2, contents.documents.size)
        assertEquals(
            listOf(document1.id, document2.id).sorted(),
            contents.documents.map { it.id }.sorted()
        )

        // Clean up
        db.deleteDocumentById(document1.id)
        db.deleteDocumentById(document2.id)
    }

    @Test
    fun `it should be possible to create a folder inside another folder`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()
        val parentFolderId = "parentFolderId"

        // First, create a parent folder
        val parentFolder = FolderApi(
            id = parentFolderId,
            title = "Parent Folder",
            parentId = "root",
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = workspaceId,
            itemCount = 0L,
        )

        val parentFolderResponse = client.post("/api/workspace/folder") {
            contentType(ContentType.Application.Json)
            setBody(SendFoldersRequest(listOf(parentFolder), workspaceId))
        }

        assertEquals(HttpStatusCode.OK, parentFolderResponse.status)

        // Create a child folder inside the parent folder
        val createFolderRequest = CreateFolderRequest(title = "Child Folder")

        val createResponse = client.post("/api/workspace/$workspaceId/folder/$parentFolderId/create") {
            contentType(ContentType.Application.Json)
            setBody(createFolderRequest)
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)

        val createdFolder = createResponse.body<FolderApi>()

        // Verify the folder was created with correct properties
        assertEquals(createFolderRequest.title, createdFolder.title)
        assertEquals(parentFolderId, createdFolder.parentId)
        assertEquals(workspaceId, createdFolder.workspaceId)
        assertEquals(0L, createdFolder.itemCount)
        assertEquals(false, createdFolder.favorite)
        // Verify ID was generated (not empty)
        assertTrue(createdFolder.id.isNotEmpty())
        assertTrue(createdFolder.id != parentFolderId)

        // Verify the folder appears in the parent folder's contents
        val contentsResponse = client.get("/api/workspace/$workspaceId/folder/$parentFolderId/contents")
        assertEquals(HttpStatusCode.OK, contentsResponse.status)

        val contents = contentsResponse.body<FolderContentResponse>()
        assertTrue(contents.folders.any { it.id == createdFolder.id })
        assertEquals(createFolderRequest.title, contents.folders.first { it.id == createdFolder.id }.title)

        // Clean up
        db.deleteDocumentById(createdFolder.id)
    }

    @Test
    fun `it should be possible to upsert a new document`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()

        val documentApi = DocumentApi(
            id = "upsertTestDocument",
            title = "New Document",
            workspaceId = workspaceId,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val upsertRequest = UpsertDocumentRequest(document = documentApi)

        val response = client.post("/api/workspace/$workspaceId/document/upsert") {
            contentType(ContentType.Application.Json)
            setBody(upsertRequest)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val upsertedDocument = response.body<DocumentApi>()

        // Verify the document was created
        assertEquals(documentApi.id, upsertedDocument.id)
        assertEquals(documentApi.title, upsertedDocument.title)
        assertEquals(workspaceId, upsertedDocument.workspaceId)
        assertEquals(documentApi.parentId, upsertedDocument.parentId)

        // Verify the document can be retrieved
        val getResponse = client.get("/api/workspace/$workspaceId/document/${documentApi.id}")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val retrievedDocument = getResponse.body<DocumentApi>()
        assertEquals(documentApi.id, retrievedDocument.id)
        assertEquals(documentApi.title, retrievedDocument.title)

        // Clean up
        db.deleteDocumentById(documentApi.id)
    }

    @Test
    fun `it should be possible to upsert an existing document to update it`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()

        // First, create a document
        val originalDocument = DocumentApi(
            id = "upsertUpdateTestDocument",
            title = "Original Title",
            workspaceId = workspaceId,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val createResponse = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(originalDocument), workspaceId))
        }

        assertEquals(HttpStatusCode.OK, createResponse.status)

        // Now update the document via upsert
        val updatedDocument = originalDocument.copy(
            title = "Updated Title",
            lastUpdatedAt = 3000L
        )

        val upsertRequest = UpsertDocumentRequest(document = updatedDocument)

        val upsertResponse = client.post("/api/workspace/$workspaceId/document/upsert") {
            contentType(ContentType.Application.Json)
            setBody(upsertRequest)
        }

        assertEquals(HttpStatusCode.OK, upsertResponse.status)

        val upsertedDocument = upsertResponse.body<DocumentApi>()

        // Verify the document was updated
        assertEquals(originalDocument.id, upsertedDocument.id)
        assertEquals("Updated Title", upsertedDocument.title)
        assertEquals(workspaceId, upsertedDocument.workspaceId)

        // Verify the updated document can be retrieved
        val getResponse = client.get("/api/workspace/$workspaceId/document/${originalDocument.id}")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val retrievedDocument = getResponse.body<DocumentApi>()
        assertEquals(originalDocument.id, retrievedDocument.id)
        assertEquals("Updated Title", retrievedDocument.title)

        // Clean up
        db.deleteDocumentById(originalDocument.id)
    }

    @Test
    fun `it should be possible to delete a folder`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()

        // First, create a folder
        val folder = FolderApi(
            id = "folderToDelete_${Random.nextInt()}",
            title = "Folder To Delete",
            parentId = "root",
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = workspaceId,
            itemCount = 0L,
        )

        val createResponse = client.post("/api/workspace/folder") {
            contentType(ContentType.Application.Json)
            setBody(SendFoldersRequest(listOf(folder), workspaceId))
        }

        assertEquals(HttpStatusCode.OK, createResponse.status)

        // Verify folder exists
        val getResponse = client.get("/api/workspace/$workspaceId/folder/${folder.id}")
        assertEquals(HttpStatusCode.OK, getResponse.status)

        // Delete the folder
        val deleteResponse = client.delete("/api/workspace/$workspaceId/folder/${folder.id}")

        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify folder is deleted
        val getAfterDeleteResponse = client.get("/api/workspace/$workspaceId/folder/${folder.id}")
        assertEquals(HttpStatusCode.NotFound, getAfterDeleteResponse.status)
    }

    @Test
    fun `it should recursively delete nested folders and documents when deleting a parent folder`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()

        // Create folder structure:
        // parentFolder
        // ├── childFolder1
        // │   ├── grandchildFolder
        // │   │   └── deepDocument
        // │   └── childDocument1
        // ├── childFolder2
        // │   └── childDocument2
        // └── parentDocument

        val parentFolder = FolderApi(
            id = "parentFolder_${Random.nextInt()}",
            title = "Parent Folder",
            parentId = "root",
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = workspaceId,
            itemCount = 0L,
        )

        val childFolder1 = FolderApi(
            id = "childFolder1_${Random.nextInt()}",
            title = "Child Folder 1",
            parentId = parentFolder.id,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = workspaceId,
            itemCount = 0L,
        )

        val childFolder2 = FolderApi(
            id = "childFolder2_${Random.nextInt()}",
            title = "Child Folder 2",
            parentId = parentFolder.id,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = workspaceId,
            itemCount = 0L,
        )

        val grandchildFolder = FolderApi(
            id = "grandchildFolder_${Random.nextInt()}",
            title = "Grandchild Folder",
            parentId = childFolder1.id,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = workspaceId,
            itemCount = 0L,
        )

        val parentDocument = DocumentApi(
            id = "parentDoc_${Random.nextInt()}",
            title = "Parent Document",
            workspaceId = workspaceId,
            parentId = parentFolder.id,
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val childDocument1 = DocumentApi(
            id = "childDoc1_${Random.nextInt()}",
            title = "Child Document 1",
            workspaceId = workspaceId,
            parentId = childFolder1.id,
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val childDocument2 = DocumentApi(
            id = "childDoc2_${Random.nextInt()}",
            title = "Child Document 2",
            workspaceId = workspaceId,
            parentId = childFolder2.id,
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val deepDocument = DocumentApi(
            id = "deepDoc_${Random.nextInt()}",
            title = "Deep Document",
            workspaceId = workspaceId,
            parentId = grandchildFolder.id,
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        // Create all folders
        val folderResponse = client.post("/api/workspace/folder") {
            contentType(ContentType.Application.Json)
            setBody(SendFoldersRequest(
                listOf(parentFolder, childFolder1, childFolder2, grandchildFolder),
                workspaceId
            ))
        }
        assertEquals(HttpStatusCode.OK, folderResponse.status)

        // Create all documents
        val documentResponse = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(
                listOf(parentDocument, childDocument1, childDocument2, deepDocument),
                workspaceId
            ))
        }
        assertEquals(HttpStatusCode.OK, documentResponse.status)

        // Verify all items exist before deletion
        assertEquals(HttpStatusCode.OK, client.get("/api/workspace/$workspaceId/folder/${parentFolder.id}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/workspace/$workspaceId/folder/${childFolder1.id}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/workspace/$workspaceId/folder/${childFolder2.id}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/workspace/$workspaceId/folder/${grandchildFolder.id}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/workspace/$workspaceId/document/${parentDocument.id}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/workspace/$workspaceId/document/${childDocument1.id}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/workspace/$workspaceId/document/${childDocument2.id}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/workspace/$workspaceId/document/${deepDocument.id}").status)

        // Delete the parent folder - should recursively delete everything
        val deleteResponse = client.delete("/api/workspace/$workspaceId/folder/${parentFolder.id}")
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify all folders are deleted
        assertEquals(HttpStatusCode.NotFound, client.get("/api/workspace/$workspaceId/folder/${parentFolder.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/workspace/$workspaceId/folder/${childFolder1.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/workspace/$workspaceId/folder/${childFolder2.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/workspace/$workspaceId/folder/${grandchildFolder.id}").status)

        // Verify all documents are deleted
        assertEquals(HttpStatusCode.NotFound, client.get("/api/workspace/$workspaceId/document/${parentDocument.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/workspace/$workspaceId/document/${childDocument1.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/workspace/$workspaceId/document/${childDocument2.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/workspace/$workspaceId/document/${deepDocument.id}").status)
    }

    @Test
    fun `it should be possible to delete a list of documents`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()

        // Create documents
        val document1 = DocumentApi(
            id = "docToDelete1_${Random.nextInt()}",
            title = "Document 1",
            workspaceId = workspaceId,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val document2 = DocumentApi(
            id = "docToDelete2_${Random.nextInt()}",
            title = "Document 2",
            workspaceId = workspaceId,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        val document3 = DocumentApi(
            id = "docToKeep_${Random.nextInt()}",
            title = "Document 3",
            workspaceId = workspaceId,
            parentId = "root",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 0L
        )

        // Save documents
        val createResponse = client.post("/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(document1, document2, document3), workspaceId))
        }

        assertEquals(HttpStatusCode.OK, createResponse.status)

        // Verify documents exist
        val getResponse1 = client.get("/api/workspace/$workspaceId/document/${document1.id}")
        assertEquals(HttpStatusCode.OK, getResponse1.status)

        val getResponse2 = client.get("/api/workspace/$workspaceId/document/${document2.id}")
        assertEquals(HttpStatusCode.OK, getResponse2.status)

        val getResponse3 = client.get("/api/workspace/$workspaceId/document/${document3.id}")
        assertEquals(HttpStatusCode.OK, getResponse3.status)

        // Delete documents 1 and 2
        val deleteRequest = DeleteDocumentsRequest(documentIds = listOf(document1.id, document2.id))

        val deleteResponse = client.post("/api/workspace/$workspaceId/document/delete") {
            contentType(ContentType.Application.Json)
            setBody(deleteRequest)
        }

        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify documents 1 and 2 are deleted
        val getAfterDelete1 = client.get("/api/workspace/$workspaceId/document/${document1.id}")
        assertEquals(HttpStatusCode.NotFound, getAfterDelete1.status)

        val getAfterDelete2 = client.get("/api/workspace/$workspaceId/document/${document2.id}")
        assertEquals(HttpStatusCode.NotFound, getAfterDelete2.status)

        // Verify document 3 still exists
        val getAfterDelete3 = client.get("/api/workspace/$workspaceId/document/${document3.id}")
        assertEquals(HttpStatusCode.OK, getAfterDelete3.status)

        // Clean up
        db.deleteDocumentById(document3.id)
    }
}
