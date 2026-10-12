package io.writeopia.api.embeddings

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * The embeddings in Postgres with pgvector. SQLDelight's PostgreSQL dialect has no `vector`
 * type, so these two tables live outside the generated schema and are reached with raw SQL
 * through the same [SqlDriver] (same pool, same connections). Vectors travel as pgvector's text
 * form, `[0.1,0.2,...]`, cast with `::vector`.
 *
 * The tables are created by `docker/postgres/init.sql` locally, by the hand-run script in
 * WriteopiaScripts2/db-migrations/document_embeddings/ in production, and by [ensureSchema] in
 * tests. All three must agree with [ddl].
 */
class PgVectorEmbeddingStore(
    private val driver: SqlDriver,
    val dimension: Int = DEFAULT_DIMENSION,
) : EmbeddingStore {

    /** True when the `vector` extension is installed in the connected database. */
    fun isAvailable(): Boolean =
        query("SELECT 1 FROM pg_extension WHERE extname = 'vector'", 0, null) { cursor -> cursor.getLong(0) }
            .isNotEmpty()

    /** Installs the extension (when allowed) and creates the tables if they don't exist. */
    fun ensureSchema() {
        ddl(dimension).forEach { statement -> driver.execute(null, statement, 0, null).value }
    }

    override fun documentMeta(documentId: String): DocumentEmbeddingMeta? =
        query(
            "SELECT document_id, model, embedded_at, checked_at FROM document_embedding WHERE document_id = ?",
            1,
            { bindString(0, documentId) }
        ) { cursor ->
            DocumentEmbeddingMeta(
                documentId = cursor.getString(0)!!,
                model = cursor.getString(1)!!,
                embeddedAt = cursor.getLong(2)!!,
                checkedAt = cursor.getLong(3)!!
            )
        }.firstOrNull()

    override fun document(documentId: String): StoredDocumentEmbedding? = documents(listOf(documentId)).firstOrNull()

    override fun documents(documentIds: Collection<String>): List<StoredDocumentEmbedding> {
        val ids = documentIds.distinct()
        if (ids.isEmpty()) return emptyList()

        return query(
            "SELECT document_id, workspace_id, model, embedding::text, source_title, source_text, embedded_at, checked_at " +
                "FROM document_embedding WHERE document_id IN ${placeholders(ids.size)}",
            ids.size,
            { ids.forEachIndexed { index, id -> bindString(index, id) } }
        ) { cursor ->
            StoredDocumentEmbedding(
                documentId = cursor.getString(0)!!,
                workspaceId = cursor.getString(1)!!,
                model = cursor.getString(2)!!,
                vector = EmbeddingVectors.fromPgText(cursor.getString(3)!!),
                sourceTitle = cursor.getString(4)!!,
                sourceText = cursor.getString(5)!!,
                embeddedAt = cursor.getLong(6)!!,
                checkedAt = cursor.getLong(7)!!
            )
        }
    }

    override fun upsertDocument(embedding: StoredDocumentEmbedding) {
        checkDimension(embedding.vector)
        driver.execute(
            null,
            "INSERT INTO document_embedding " +
                "(document_id, workspace_id, model, embedding, source_title, source_text, embedded_at, checked_at) " +
                "VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?) " +
                "ON CONFLICT (document_id) DO UPDATE SET " +
                "workspace_id = EXCLUDED.workspace_id, model = EXCLUDED.model, embedding = EXCLUDED.embedding, " +
                "source_title = EXCLUDED.source_title, source_text = EXCLUDED.source_text, " +
                "embedded_at = EXCLUDED.embedded_at, checked_at = EXCLUDED.checked_at",
            8
        ) {
            bindString(0, embedding.documentId)
            bindString(1, embedding.workspaceId)
            bindString(2, embedding.model)
            bindString(3, EmbeddingVectors.toPgText(embedding.vector))
            bindString(4, embedding.sourceTitle)
            bindString(5, embedding.sourceText)
            bindLong(6, embedding.embeddedAt)
            bindLong(7, embedding.checkedAt)
        }.value
    }

    override fun markDocumentChecked(documentId: String, checkedAt: Long) {
        driver.execute(null, "UPDATE document_embedding SET checked_at = ? WHERE document_id = ?", 2) {
            bindLong(0, checkedAt)
            bindString(1, documentId)
        }.value
    }

    override fun deleteDocuments(documentIds: Collection<String>) {
        val ids = documentIds.distinct()
        if (ids.isEmpty()) return
        driver.execute(
            null,
            "DELETE FROM document_embedding WHERE document_id IN ${placeholders(ids.size)}",
            ids.size
        ) { ids.forEachIndexed { index, id -> bindString(index, id) } }.value
    }

    override fun deleteDocumentsByWorkspace(workspaceId: String) {
        driver.execute(null, "DELETE FROM document_embedding WHERE workspace_id = ?", 1) {
            bindString(0, workspaceId)
        }.value
    }

    override fun folder(folderId: String): StoredFolderEmbedding? = folders(listOf(folderId)).firstOrNull()

    override fun folders(folderIds: Collection<String>): List<StoredFolderEmbedding> {
        val ids = folderIds.distinct()
        if (ids.isEmpty()) return emptyList()

        return query(
            "SELECT folder_id, workspace_id, model, embedding::text, kind, document_count, source_title, updated_at " +
                "FROM folder_embedding WHERE folder_id IN ${placeholders(ids.size)}",
            ids.size,
            { ids.forEachIndexed { index, id -> bindString(index, id) } }
        ) { cursor ->
            StoredFolderEmbedding(
                folderId = cursor.getString(0)!!,
                workspaceId = cursor.getString(1)!!,
                model = cursor.getString(2)!!,
                vector = EmbeddingVectors.fromPgText(cursor.getString(3)!!),
                kind = FolderEmbeddingKind.fromDb(cursor.getString(4)!!),
                documentCount = cursor.getLong(5)!!.toInt(),
                sourceTitle = cursor.getString(6)!!,
                updatedAt = cursor.getLong(7)!!
            )
        }
    }

    override fun upsertFolder(embedding: StoredFolderEmbedding) {
        checkDimension(embedding.vector)
        driver.execute(
            null,
            "INSERT INTO folder_embedding " +
                "(folder_id, workspace_id, model, embedding, kind, document_count, source_title, updated_at) " +
                "VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?) " +
                "ON CONFLICT (folder_id) DO UPDATE SET " +
                "workspace_id = EXCLUDED.workspace_id, model = EXCLUDED.model, embedding = EXCLUDED.embedding, " +
                "kind = EXCLUDED.kind, document_count = EXCLUDED.document_count, " +
                "source_title = EXCLUDED.source_title, updated_at = EXCLUDED.updated_at",
            8
        ) {
            bindString(0, embedding.folderId)
            bindString(1, embedding.workspaceId)
            bindString(2, embedding.model)
            bindString(3, EmbeddingVectors.toPgText(embedding.vector))
            bindString(4, embedding.kind.dbValue)
            bindLong(5, embedding.documentCount.toLong())
            bindString(6, embedding.sourceTitle)
            bindLong(7, embedding.updatedAt)
        }.value
    }

    override fun deleteFolders(folderIds: Collection<String>) {
        val ids = folderIds.distinct()
        if (ids.isEmpty()) return
        driver.execute(
            null,
            "DELETE FROM folder_embedding WHERE folder_id IN ${placeholders(ids.size)}",
            ids.size
        ) { ids.forEachIndexed { index, id -> bindString(index, id) } }.value
    }

    override fun deleteFoldersByWorkspace(workspaceId: String) {
        driver.execute(null, "DELETE FROM folder_embedding WHERE workspace_id = ?", 1) {
            bindString(0, workspaceId)
        }.value
    }

    override fun rankFolders(workspaceId: String, query: FloatArray, model: String, limit: Int): List<RankedFolder> {
        checkDimension(query)
        val queryText = EmbeddingVectors.toPgText(query)

        // `<=>` is pgvector's cosine distance; the score is the similarity.
        return query(
            "SELECT folder_id, kind, 1 - (embedding <=> ?::vector) AS score " +
                "FROM folder_embedding WHERE workspace_id = ? AND model = ? " +
                "ORDER BY embedding <=> ?::vector LIMIT ?",
            5,
            {
                bindString(0, queryText)
                bindString(1, workspaceId)
                bindString(2, model)
                bindString(3, queryText)
                bindLong(4, limit.toLong())
            }
        ) { cursor ->
            RankedFolder(
                folderId = cursor.getString(0)!!,
                kind = FolderEmbeddingKind.fromDb(cursor.getString(1)!!),
                score = cursor.getDouble(2) ?: 0.0
            )
        }
    }

    private fun <T> query(
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
        mapRow: (SqlCursor) -> T,
    ): List<T> =
        driver.executeQuery(
            null,
            sql,
            { cursor ->
                val rows = mutableListOf<T>()
                while (cursor.next().value) rows.add(mapRow(cursor))
                QueryResult.Value(rows.toList())
            },
            parameters,
            binders
        ).value

    private fun checkDimension(vector: FloatArray) {
        require(vector.size == dimension) {
            "Expected a vector of $dimension dimensions, got ${vector.size}"
        }
    }

    private fun placeholders(count: Int): String = (1..count).joinToString(separator = ", ", prefix = "(", postfix = ")") { "?" }

    companion object {
        const val DEFAULT_DIMENSION = 768

        /** The schema, one statement per element. Kept in sync with init.sql and the production script. */
        fun ddl(dimension: Int = DEFAULT_DIMENSION): List<String> = listOf(
            "CREATE EXTENSION IF NOT EXISTS vector",
            """
            CREATE TABLE IF NOT EXISTS document_embedding (
              document_id   TEXT PRIMARY KEY,
              workspace_id  TEXT NOT NULL,
              model         TEXT NOT NULL,
              embedding     vector($dimension) NOT NULL,
              source_title  TEXT NOT NULL,
              source_text   TEXT NOT NULL,
              embedded_at   BIGINT NOT NULL,
              checked_at    BIGINT NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_document_embedding_workspace_id ON document_embedding(workspace_id)",
            """
            CREATE TABLE IF NOT EXISTS folder_embedding (
              folder_id       TEXT PRIMARY KEY,
              workspace_id    TEXT NOT NULL,
              model           TEXT NOT NULL,
              embedding       vector($dimension) NOT NULL,
              kind            TEXT NOT NULL,
              document_count  INTEGER NOT NULL,
              source_title    TEXT NOT NULL,
              updated_at      BIGINT NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_folder_embedding_workspace_id ON folder_embedding(workspace_id)",
        )
    }
}
