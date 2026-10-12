package io.writeopia.api.embeddings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryEmbeddingStoreTest {

    private fun folder(id: String, vector: FloatArray, model: String = "fake") = StoredFolderEmbedding(
        folderId = id,
        workspaceId = "ws",
        model = model,
        vector = vector,
        kind = FolderEmbeddingKind.CENTROID,
        documentCount = 1,
        sourceTitle = id,
        updatedAt = 0,
    )

    @Test
    fun `folders rank by cosine similarity within the workspace and model`() {
        val store = InMemoryEmbeddingStore()
        store.upsertFolder(folder("close", floatArrayOf(1f, 0.1f)))
        store.upsertFolder(folder("far", floatArrayOf(0f, 1f)))
        store.upsertFolder(folder("middle", floatArrayOf(1f, 1f)))
        store.upsertFolder(folder("other-model", floatArrayOf(1f, 0f), model = "other"))

        val ranked = store.rankFolders("ws", floatArrayOf(1f, 0f), "fake", limit = 2)

        assertEquals(listOf("close", "middle"), ranked.map { it.folderId })
        assertEquals(emptyList(), store.rankFolders("another-ws", floatArrayOf(1f, 0f), "fake", 5))
    }

    @Test
    fun `documents are stored, read by ids and deleted`() {
        val store = InMemoryEmbeddingStore()
        val row = StoredDocumentEmbedding("d1", "ws", "fake", floatArrayOf(1f), "t", "body", 10)
        store.upsertDocument(row)

        assertEquals(DocumentEmbeddingMeta("d1", "fake", 10), store.documentMeta("d1"))
        assertEquals(listOf(row), store.documents(listOf("d1", "missing")))

        store.deleteDocumentsByWorkspace("ws")

        assertNull(store.document("d1"))
    }
}
