package io.writeopia.api.embeddings

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs against a real Postgres with pgvector (the docker-compose database locally, the CI
 * service), reached with the DB_* variables the backend uses. The embedded Postgres of the other
 * tests has no pgvector, so this test skips itself when no such database answers.
 */
class PgVectorEmbeddingStoreTest {

    private fun storeOrNull(): Pair<PgVectorEmbeddingStore, HikariDataSource>? {
        val config = HikariConfig().apply {
            val host = System.getenv("DB_HOST") ?: "localhost"
            val port = System.getenv("DB_PORT") ?: "5432"
            val name = System.getenv("DB_NAME") ?: "writeopia"
            jdbcUrl = System.getenv("JDBC_URL") ?: "jdbc:postgresql://$host:$port/$name"
            username = System.getenv("DB_USER") ?: "postgres"
            password = System.getenv("DB_PASS") ?: "postgres"
            maximumPoolSize = 2
            isAutoCommit = true
            connectionTimeout = 3_000
            initializationFailTimeout = -1
        }

        return try {
            val dataSource = HikariDataSource(config)
            dataSource.connection.use { }
            val store = PgVectorEmbeddingStore(dataSource.asJdbcDriver(), dimension = 3)
            store.ensureSchema()
            if (store.isAvailable()) store to dataSource else null
        } catch (e: Exception) {
            println("PgVectorEmbeddingStoreTest skipped: no Postgres with pgvector reachable (${e.message})")
            null
        }
    }

    @Test
    fun `documents and folders round trip and folders rank by distance`() {
        val (store, dataSource) = storeOrNull() ?: return
        val workspace = "ws-${System.nanoTime()}"

        try {
            val document = StoredDocumentEmbedding(
                documentId = "$workspace-doc",
                workspaceId = workspace,
                model = "fake",
                vector = floatArrayOf(1f, 0f, 0f),
                sourceTitle = "Title",
                sourceText = "Body",
                embeddedAt = 42,
            )
            store.upsertDocument(document)
            store.upsertDocument(document.copy(sourceText = "Body 2", embeddedAt = 43))

            val read = store.document(document.documentId)!!
            assertEquals("Body 2", read.sourceText)
            assertEquals(43, read.embeddedAt)
            assertEquals(listOf(1f, 0f, 0f), read.vector.toList())
            assertEquals(DocumentEmbeddingMeta(document.documentId, "fake", 43), store.documentMeta(document.documentId))

            fun folder(id: String, vector: FloatArray) = StoredFolderEmbedding(
                folderId = "$workspace-$id",
                workspaceId = workspace,
                model = "fake",
                vector = vector,
                kind = FolderEmbeddingKind.CENTROID,
                documentCount = 2,
                sourceTitle = id,
                updatedAt = 1,
            )
            store.upsertFolder(folder("close", floatArrayOf(1f, 0.1f, 0f)))
            store.upsertFolder(folder("far", floatArrayOf(0f, 0f, 1f)))
            store.upsertFolder(folder("middle", floatArrayOf(1f, 1f, 0f)))

            val ranked = store.rankFolders(workspace, floatArrayOf(1f, 0f, 0f), "fake", limit = 2)
            assertEquals(listOf("$workspace-close", "$workspace-middle"), ranked.map { it.folderId })
            assertTrue(ranked[0].score > ranked[1].score)

            val folders = store.folders(listOf("$workspace-far"))
            assertEquals(FolderEmbeddingKind.CENTROID, folders.single().kind)
            assertEquals(2, folders.single().documentCount)

            store.deleteDocumentsByWorkspace(workspace)
            store.deleteFoldersByWorkspace(workspace)
            assertNull(store.document(document.documentId))
            assertEquals(emptyList(), store.rankFolders(workspace, floatArrayOf(1f, 0f, 0f), "fake", 5))
        } finally {
            store.deleteDocumentsByWorkspace(workspace)
            store.deleteFoldersByWorkspace(workspace)
            dataSource.close()
        }
    }
}
