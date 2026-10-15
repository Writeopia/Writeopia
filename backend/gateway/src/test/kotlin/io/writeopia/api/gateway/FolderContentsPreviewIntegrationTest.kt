@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.writeopia.api.documents.documents.repository.deleteDocumentById
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.FolderApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.json.SendFoldersRequest
import io.writeopia.sdk.serialization.response.FolderContentResponse
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FolderContentsPreviewIntegrationTest {

    private val db = configurePersistence()

    private fun document(id: String, workspaceId: String, parentId: String, steps: Int) = DocumentApi(
        id = id,
        title = "Document $id",
        workspaceId = workspaceId,
        parentId = parentId,
        isLocked = false,
        createdAt = 1000L,
        lastUpdatedAt = 2000L,
        lastSyncedAt = 0L,
        content = (0 until steps).map { index ->
            StoryStep(type = StoryTypes.TEXT.type, text = "step $index").toApi(index.toDouble())
        }
    )

    private suspend fun ApplicationTestBuilder.saveDocuments(workspaceId: String, vararg documents: DocumentApi) {
        val response = defaultClient().post("/api/docs/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documents.toList(), workspaceId))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `each document brings only its first seven steps by default`() = testApplication {
        application { module(db, debugMode = true) }
        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()
        val folderId = "previewFolder_${Random.nextInt()}"
        val long = document("long_${Random.nextInt()}", workspaceId, folderId, steps = 12)
        val short = document("short_${Random.nextInt()}", workspaceId, folderId, steps = 3)
        saveDocuments(workspaceId, long, short)

        val response = client.get("/api/docs/workspace/$workspaceId/folder/$folderId/contents/preview")
        assertEquals(HttpStatusCode.OK, response.status)
        val contents = response.body<FolderContentResponse>()

        val byId = contents.documents.associateBy { it.id }
        val longContent = byId.getValue(long.id).content
        assertEquals((0 until 7).map { "step $it" }, longContent.sortedBy { it.position }.map { it.text })
        assertEquals(3, byId.getValue(short.id).content.size)

        db.deleteDocumentById(long.id, short.id)
    }

    @Test
    fun `the number of steps can be chosen`() = testApplication {
        application { module(db, debugMode = true) }
        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()
        val folderId = "previewFolder_${Random.nextInt()}"
        val doc = document("doc_${Random.nextInt()}", workspaceId, folderId, steps = 12)
        saveDocuments(workspaceId, doc)

        val two = client.get("/api/docs/workspace/$workspaceId/folder/$folderId/contents/preview?steps=2")
            .body<FolderContentResponse>()
        assertEquals(listOf("step 0", "step 1"), two.documents.single().content.sortedBy { it.position }.map { it.text })

        val none = client.get("/api/docs/workspace/$workspaceId/folder/$folderId/contents/preview?steps=0")
            .body<FolderContentResponse>()
        assertEquals(doc.id, none.documents.single().id)
        assertTrue(none.documents.single().content.isEmpty())

        db.deleteDocumentById(doc.id)
    }

    @Test
    fun `an invalid number of steps is refused`() = testApplication {
        application { module(db, debugMode = true) }
        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()

        listOf("abc", "-1", "101").forEach { steps ->
            val response = client.get("/api/docs/workspace/$workspaceId/folder/root/contents/preview?steps=$steps")
            assertEquals(HttpStatusCode.BadRequest, response.status, "steps=$steps")
        }
    }

    @Test
    fun `the root only lists the items of its own workspace`() = testApplication {
        application { module(db, debugMode = true) }
        val client = defaultClient()
        val workspaceId = Random.nextInt().toString()
        val otherWorkspaceId = Random.nextInt().toString()
        val mine = document("mine_${Random.nextInt()}", workspaceId, "root", steps = 1)
        val theirs = document("theirs_${Random.nextInt()}", otherWorkspaceId, "root", steps = 1)
        saveDocuments(workspaceId, mine)
        saveDocuments(otherWorkspaceId, theirs)

        val theirFolder = FolderApi(
            id = "theirFolder_${Random.nextInt()}",
            title = "Their folder",
            parentId = "root",
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = otherWorkspaceId,
            itemCount = 0L,
        )
        client.post("/api/docs/workspace/folder") {
            contentType(ContentType.Application.Json)
            setBody(SendFoldersRequest(listOf(theirFolder), otherWorkspaceId))
        }

        val contents = client.get("/api/docs/workspace/$workspaceId/folder/root/contents/preview")
            .body<FolderContentResponse>()

        assertTrue(contents.documents.any { it.id == mine.id })
        assertTrue(contents.documents.none { it.id == theirs.id })
        assertTrue(contents.folders.none { it.id == theirFolder.id })

        db.deleteDocumentById(mine.id, theirs.id)
    }
}
