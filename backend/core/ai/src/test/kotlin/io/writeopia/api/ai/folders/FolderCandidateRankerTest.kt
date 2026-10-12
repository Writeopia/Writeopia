package io.writeopia.api.ai.folders

import io.writeopia.api.ai.configureTestPersistence
import io.writeopia.api.embeddings.Embedder
import io.writeopia.api.embeddings.EmbeddingPurpose
import io.writeopia.api.embeddings.Embeddings
import io.writeopia.api.embeddings.FolderEmbeddingKind
import io.writeopia.api.embeddings.InMemoryEmbeddingStore
import io.writeopia.api.embeddings.StoredFolderEmbedding
import io.writeopia.app.dto.writingagent.FolderCandidate
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderCandidateRankerTest {

    private val db: WriteopiaDbBackend = configureTestPersistence()
    private val workspace = "ws-${UUID.randomUUID()}"

    /** Embeds "cooking" texts along the first axis and "work" texts along the second. */
    private class AxisEmbedder(var answer: Boolean = true) : Embedder {
        override val model = "fake"
        override val dimension = 2
        var calls = 0

        override suspend fun embed(text: String, purpose: EmbeddingPurpose): FloatArray? {
            calls++
            if (!answer) return null
            val cooking = if (text.contains("recipe", ignoreCase = true)) 1f else 0.1f
            val work = if (text.contains("meeting", ignoreCase = true)) 1f else 0.1f
            return floatArrayOf(cooking, work)
        }
    }

    private fun folder(id: String, title: String, parentId: String = "root", updatedAt: Long = 1_000) {
        db.folderEntityQueries.insert(
            id = id,
            parent_id = parentId,
            workspace_id = workspace,
            title = title,
            created_at = 1,
            last_updated_at = updatedAt,
            favorite = false,
            icon = null,
            icon_tint = null,
            last_synced_at = updatedAt,
        )
    }

    private fun document(id: String, title: String, parentId: String, updatedAt: Long = 1_000) {
        db.documentEntityQueries.insert(
            id = id,
            title = title,
            created_at = 1,
            last_updated_at = updatedAt,
            last_synced = updatedAt,
            workspace_id = workspace,
            favorite = false,
            parent_document_id = parentId,
            icon = null,
            icon_tint = null,
            is_locked = false,
            company_id = "",
            deleted = false,
            published = false,
        )
    }

    private fun tag(documentId: String, tagId: String, name: String) {
        db.documentTagEntityQueries.insert(
            id = tagId,
            workspace_id = workspace,
            name = name,
            description = "",
            created_at = 1,
            last_updated_at = 1,
            deleted = false,
        )
        db.documentTagLinkEntityQueries.insert(document_id = documentId, tag_id = tagId, workspace_id = workspace)
    }

    private fun vector(folderId: String, vector: FloatArray, store: InMemoryEmbeddingStore, count: Int = 1) {
        store.upsertFolder(
            StoredFolderEmbedding(
                folderId = folderId,
                workspaceId = workspace,
                model = "fake",
                vector = vector,
                kind = if (count == 0) FolderEmbeddingKind.TITLE else FolderEmbeddingKind.CENTROID,
                documentCount = count,
                sourceTitle = folderId,
                updatedAt = 1,
            )
        )
    }

    private val request = FolderSuggestionsRequest(
        documentTitle = "Carbonara",
        documentText = "A recipe with eggs and cheese.",
        folders = listOf(FolderCandidate("app-folder", "From the app")),
        workspaceId = workspace,
    )

    @Test
    fun `without a workspace the app's folders are kept`() = runTest {
        val outcome = FolderCandidateRanker(embeddings = null).candidates(request.copy(workspaceId = ""), db)

        assertEquals(FolderCandidateRanker.Source.APP, outcome.source)
        assertEquals(listOf("app-folder"), outcome.candidates.map { it.id })
    }

    @Test
    fun `folders rank by their vectors, the rest by activity, and each carries its profile`() = runTest {
        folder("$workspace-cooking", "Cooking", updatedAt = 10)
        folder("$workspace-italian", "Italian", parentId = "$workspace-cooking", updatedAt = 20)
        folder("$workspace-work", "Work", updatedAt = 30)
        folder("$workspace-new", "Brand new", updatedAt = 40)
        document("$workspace-d1", "Pesto", "$workspace-italian", updatedAt = 5)
        document("$workspace-d2", "Lasagna", "$workspace-italian", updatedAt = 9)
        tag("$workspace-d2", "$workspace-t1", "recipes")

        val store = InMemoryEmbeddingStore()
        // The query of a recipe is (1, 0.1): Italian matches it exactly, Cooking is close, Work is far.
        vector("$workspace-italian", floatArrayOf(1f, 0.1f), store, count = 2)
        vector("$workspace-cooking", floatArrayOf(0.9f, 0f), store, count = 2)
        vector("$workspace-work", floatArrayOf(0f, 1f), store)
        val embedder = AxisEmbedder()

        val outcome = FolderCandidateRanker(Embeddings(embedder, store)).candidates(request, db)

        assertEquals(FolderCandidateRanker.Source.VECTORS, outcome.source)
        assertEquals(
            listOf("$workspace-italian", "$workspace-cooking", "$workspace-work", "$workspace-new"),
            outcome.candidates.map { it.id }
        )
        assertEquals(1, embedder.calls)

        val italian = outcome.candidates.first()
        assertEquals("Italian", italian.name)
        assertEquals("Cooking", italian.path)
        assertEquals(listOf("Lasagna", "Pesto"), italian.recentTitles)
        assertEquals(listOf("recipes"), italian.tags)
        assertEquals("", outcome.candidates.last().path)
        assertTrue(outcome.candidates.last().recentTitles.isEmpty())
    }

    @Test
    fun `vectors of gone or unnamed folders cost no place and the limit holds`() = runTest {
        folder("$workspace-a", "A", updatedAt = 1)
        folder("$workspace-b", "B", updatedAt = 2)
        folder("$workspace-c", "C", updatedAt = 3)
        folder("$workspace-untitled", "", updatedAt = 4)

        val store = InMemoryEmbeddingStore()
        vector("$workspace-deleted", floatArrayOf(1f, 0.1f), store)
        vector("$workspace-untitled", floatArrayOf(1f, 0.1f), store)
        vector("$workspace-c", floatArrayOf(0.9f, 0.1f), store)

        val outcome = FolderCandidateRanker(Embeddings(AxisEmbedder(), store), limit = 2).candidates(request, db)

        assertEquals(FolderCandidateRanker.Source.VECTORS, outcome.source)
        assertEquals(listOf("$workspace-c", "$workspace-b"), outcome.candidates.map { it.id })
    }

    @Test
    fun `when the document cannot be embedded folders come by activity`() = runTest {
        folder("$workspace-old", "Old", updatedAt = 1)
        folder("$workspace-recent", "Recent", updatedAt = 99)
        folder("$workspace-untitled", "", updatedAt = 100)

        val outcome = FolderCandidateRanker(Embeddings(AxisEmbedder(answer = false), InMemoryEmbeddingStore()))
            .candidates(request, db)

        assertEquals(FolderCandidateRanker.Source.ACTIVITY, outcome.source)
        assertEquals(listOf("$workspace-recent", "$workspace-old"), outcome.candidates.map { it.id })
    }

    @Test
    fun `a workspace without folders falls back to the app's list`() = runTest {
        val outcome = FolderCandidateRanker(Embeddings(AxisEmbedder(), InMemoryEmbeddingStore())).candidates(request, db)

        assertEquals(FolderCandidateRanker.Source.APP, outcome.source)
        assertEquals(listOf("app-folder"), outcome.candidates.map { it.id })
    }
}
