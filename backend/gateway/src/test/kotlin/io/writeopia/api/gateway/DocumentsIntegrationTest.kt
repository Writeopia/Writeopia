package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.api.documents.documents.repository.deleteDocumentById
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.serialization.data.DocumentApi
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    private val db = configurePersistence()

    @Test
    fun `it should be possible to save and query document by id`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val documentApiList = listOf(
            DocumentApi(
                id = "testiaskkakakaka",
                title = "Test Note",
                userId = "",
                parentId = "parentIdddd",
                isLocked = false,
                createdAt = 1000L,
                lastUpdatedAt = 2000L,
                lastSyncedAt = 2000
            )
        )

        val response = client.post("/api/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documentApiList))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.get("/api/document/${documentApiList.first().id}")

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(documentApiList.first(), response1.body())

        documentApiList.forEach { documentApi ->
            db.deleteDocumentById(documentApi.id)
        }
    }

    @Test
    fun `it should be possible to save and query documents by parent id`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        val documentApiList = listOf(
            DocumentApi(
                id = "testiaskkakakaka",
                title = "Test Note",
                userId = "",
                parentId = "parentIdddd",
                isLocked = false,
                createdAt = 1000L,
                lastUpdatedAt = 2000L,
                lastSyncedAt = 2000
            )
        )

        val response = client.post("/api/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documentApiList))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.get(
            "/api/document/parent/${documentApiList.first().parentId}"
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

        val documentApi = DocumentApi(
            id = "testias",
            title = "Test Note",
            userId = "",
            parentId = "parentId",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L
        )

        val response = client.post("/api/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi)))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.get("/api/document/parent/${documentApi.parentId}")

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

        val documentApi = DocumentApi(
            id = "testias",
            title = "Test Note",
            userId = "",
            parentId = "parentId",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L,
            lastSyncedAt = 4000
        )

        val documentApi2 = documentApi.copy(id = "testias2", lastUpdatedAt = 4000L)

        val response = client.post("/api/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi)))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.post("/api/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(documentApi2)))
        }

        assertEquals(HttpStatusCode.OK, response1.status)

        val request = FolderDiffRequest(
            folderId = "parentId",
            lastFolderSync = 3000L
        )

        val response2 = client.post("/api/document/folder/diff") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.OK, response2.status)
        assertEquals(listOf(documentApi2), response2.body())

        db.deleteDocumentById(documentApi.id)
        db.deleteDocumentById(documentApi2.id)
    }
}
