@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.embeddings.Embedder
import io.writeopia.api.embeddings.EmbeddingPurpose
import io.writeopia.api.embeddings.EmbeddingVectors
import io.writeopia.api.embeddings.FolderEmbeddingKind
import io.writeopia.api.embeddings.InMemoryEmbeddingStore
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.FolderApi
import io.writeopia.sdk.serialization.data.StoryStepApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.request.CreateFolderRequest
import io.writeopia.sdk.serialization.request.DeleteDocumentsRequest
import io.writeopia.sdk.serialization.request.MoveDocumentRequest
import io.writeopia.sdk.serialization.request.StoryStepChangeApi
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The embeddings of documents and folders as the documents routes save, sync, move and delete,
 * with a deterministic embedder and the in-memory store (the embedded Postgres has no pgvector).
 */
class DocumentEmbeddingsIntegrationTest {

    private val db = configurePersistence()

    /** A vector from the text itself, so equal texts give equal vectors and means are checkable. */
    private class CountingEmbedder : Embedder {
        override val model = "fake"
        override val dimension = 4
        val calls = mutableListOf<String>()

        override suspend fun embed(text: String, purpose: EmbeddingPurpose): FloatArray {
            calls.add(text)
            return floatArrayOf(
                text.length.toFloat(),
                text.count { it == 'a' }.toFloat(),
                text.count { it == 'e' }.toFloat(),
                1f
            )
        }
    }

    private fun step(id: String, text: String, type: StoryTypes = StoryTypes.TEXT, position: Double) =
        StoryStep(id = id, type = type.type, text = text).toApi(position)

    private fun document(id: String, workspace: String, parentId: String, title: String, steps: List<StoryStepApi>) =
        DocumentApi(
            id = id,
            title = title,
            workspaceId = workspace,
            content = steps,
            parentId = parentId,
            lastSyncedAt = 0L,
        )

    @Test
    fun `documents are embedded on big changes and folders follow their documents`() = testApplication {
        val embedder = CountingEmbedder()
        val store = InMemoryEmbeddingStore()
        application {
            module(db, debugMode = true, embedder = embedder, embeddingStore = store)
        }

        val client = defaultClient()
        val workspace = "ws-${Random.nextInt()}"
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. A saved document gets its embedding.
        val pastaSteps = listOf(
            step("$workspace-t1", "Pasta", StoryTypes.TITLE, 0.0),
            step("$workspace-s1", "Boil a lot of water in a large pan and salt it generously.", position = 1.0),
            step("$workspace-s2", "Cook the pasta until al dente, then drain and serve at once.", position = 2.0),
        )
        val pasta = document("$workspace-pasta", workspace, "root", "Pasta", pastaSteps)
        assertEquals(
            HttpStatusCode.OK,
            client.post("/api/docs/workspace/document") {
                contentType(ContentType.Application.Json)
                setBody(SendDocumentsRequest(listOf(pasta), workspace))
            }.status
        )

        val firstEmbedding = assertNotNull(store.document(pasta.id))
        assertEquals("Pasta", firstEmbedding.sourceTitle)
        assertEquals(1, embedder.calls.size)
        assertTrue(embedder.calls.single().startsWith("Pasta\n\nBoil"))

        // 2. A small edit synced while typing does not embed again.
        fun sync(vararg changes: StoryStepApi) = StoryStepSyncRequest(
            documentId = pasta.id,
            workspaceId = workspace,
            lastSyncTimestamp = 0,
            requestTimestamp = now,
            // Stamped now, else the server keeps its own newer copy of a step.
            changes = changes.map { change -> StoryStepChangeApi(change.copy(lastUpdatedAt = now), change.position) },
            deletions = emptyList(),
        )
        suspend fun syncSteps(request: StoryStepSyncRequest) {
            val response = client.post("/api/docs/workspace/$workspace/document/${pasta.id}/steps/sync") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        // Pretend the first embedding is old, so the cooldown is not what holds the next one back.
        store.upsertDocument(firstEmbedding.copy(embeddedAt = firstEmbedding.embeddedAt - 60_000))
        syncSteps(sync(step("$workspace-s1", "Boil a lot of water in a large pot and salt it generously.", position = 1.0)))
        assertEquals(1, embedder.calls.size)

        // 3. A big change is embedded again once the cooldown passed, not before.
        val rewrite = sync(
            step("$workspace-s1", "Whisk the eggs with the grated cheese and plenty of black pepper.", position = 1.0),
            step("$workspace-s2", "Toss the hot pasta in the eggs away from the heat so they do not scramble.", position = 2.0),
            step("$workspace-s3", "Add a splash of the cooking water to loosen the sauce before serving.", position = 3.0),
        )
        store.upsertDocument(store.document(pasta.id)!!.copy(embeddedAt = now))
        syncSteps(rewrite)
        assertEquals(1, embedder.calls.size)

        store.upsertDocument(store.document(pasta.id)!!.copy(embeddedAt = now - 60_000))
        syncSteps(rewrite)
        assertEquals(2, embedder.calls.size)
        val pastaVector = store.document(pasta.id)!!.vector
        assertTrue(store.document(pasta.id)!!.sourceText.contains("Whisk"))

        // 4. An empty folder is known by its title.
        val cooking = client.post("/api/docs/workspace/$workspace/folder/root/create") {
            contentType(ContentType.Application.Json)
            setBody(CreateFolderRequest(title = "Cooking"))
        }.body<FolderApi>()
        assertEquals(FolderEmbeddingKind.TITLE, store.folder(cooking.id)?.kind)
        assertEquals("Cooking", embedder.calls.last())

        // 5. A document moved in makes the folder the mean of its documents.
        assertEquals(
            HttpStatusCode.OK,
            client.post("/api/docs/workspace/$workspace/document/${pasta.id}/move") {
                contentType(ContentType.Application.Json)
                setBody(MoveDocumentRequest(cooking.id))
            }.status
        )
        var cookingRow = assertNotNull(store.folder(cooking.id))
        assertEquals(FolderEmbeddingKind.CENTROID, cookingRow.kind)
        assertEquals(1, cookingRow.documentCount)
        assertEquals(pastaVector.toList(), cookingRow.vector.toList())

        // 6. A second document saved straight into the folder: the mean of both.
        val risotto = document(
            "$workspace-risotto", workspace, cooking.id, "Risotto",
            listOf(
                step("$workspace-t2", "Risotto", StoryTypes.TITLE, 0.0),
                step("$workspace-r1", "Toast the rice in butter, then add the stock one ladle at a time.", position = 1.0),
            )
        )
        client.post("/api/docs/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(listOf(risotto), workspace))
        }
        val risottoVector = assertNotNull(store.document(risotto.id)).vector
        cookingRow = store.folder(cooking.id)!!
        assertEquals(2, cookingRow.documentCount)
        assertEquals(
            EmbeddingVectors.mean(listOf(pastaVector, risottoVector))!!.toList(),
            cookingRow.vector.toList()
        )

        // 7. A nested folder counts in its parent with the documents it holds.
        val italian = client.post("/api/docs/workspace/$workspace/folder/${cooking.id}/create") {
            contentType(ContentType.Application.Json)
            setBody(CreateFolderRequest(title = "Italian"))
        }.body<FolderApi>()
        client.post("/api/docs/workspace/$workspace/document/${risotto.id}/move") {
            contentType(ContentType.Application.Json)
            setBody(MoveDocumentRequest(italian.id))
        }
        val italianRow = assertNotNull(store.folder(italian.id))
        assertEquals(FolderEmbeddingKind.CENTROID, italianRow.kind)
        assertEquals(risottoVector.toList(), italianRow.vector.toList())
        cookingRow = store.folder(cooking.id)!!
        assertEquals(2, cookingRow.documentCount)
        assertEquals(
            EmbeddingVectors.mean(listOf(pastaVector, risottoVector))!!.toList(),
            cookingRow.vector.toList()
        )

        // 8. Moved out to the root, the child folder falls back to its title and the parent shrinks.
        client.post("/api/docs/workspace/$workspace/document/${risotto.id}/move") {
            contentType(ContentType.Application.Json)
            setBody(MoveDocumentRequest("root"))
        }
        assertEquals(FolderEmbeddingKind.TITLE, store.folder(italian.id)?.kind)
        assertEquals(1, store.folder(cooking.id)?.documentCount)

        // 9. Deleting the last document leaves the folder with its title; the document's vector goes.
        client.post("/api/docs/workspace/$workspace/document/delete") {
            contentType(ContentType.Application.Json)
            setBody(DeleteDocumentsRequest(listOf(pasta.id)))
        }
        assertNull(store.document(pasta.id))
        assertEquals(FolderEmbeddingKind.TITLE, store.folder(cooking.id)?.kind)

        // 10. Deleting the folder tree removes its vectors.
        assertEquals(
            HttpStatusCode.OK,
            client.delete("/api/docs/workspace/$workspace/folder/${cooking.id}").status
        )
        assertNull(store.folder(cooking.id))
        assertNull(store.folder(italian.id))
        assertNotNull(store.document(risotto.id))
    }
}
