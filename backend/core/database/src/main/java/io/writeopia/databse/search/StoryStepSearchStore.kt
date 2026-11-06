package io.writeopia.databse.search

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * Full-text search over the text of the story steps, for the global search of a workspace.
 *
 * PostgreSQL's own full-text search (`to_tsvector`/`to_tsquery`, `simple` configuration) over
 * `story_step_entity.text`, served by an expression GIN index. The SQLDelight PostgreSQL dialect
 * only accepts `@@` against a tsvector column, so the index and the query live here as raw SQL,
 * like the embeddings and the tombstones. The `@@` expression is the index expression word for
 * word: Postgres uses the index only when the two match.
 *
 * The index is created by `docker/postgres/init.sql` locally, by the production script in
 * WriteopiaScripts2 (`db-migrations/story_step_search/1_schema.sql`), and by [ensureSchema] in
 * tests and debug mode. All three must agree with [ddl]. The query works without the index (a
 * sequential scan), so the deploy order doesn't matter.
 */
class StoryStepSearchStore(private val driver: SqlDriver) {

    /** Creates the index if it doesn't exist. */
    fun ensureSchema() {
        ddl().forEach { statement -> driver.execute(null, statement, 0, null).value }
    }

    /**
     * The documents of [workspaceId] with a text step matching every word of [rawQuery] (by
     * prefix), one match per document, the best ranked first. Only the steps that hold prose
     * count (message, check item, list item, code block): the title is matched by the title
     * search. Deleted documents are left out.
     */
    fun search(workspaceId: String, rawQuery: String, limit: Int = DEFAULT_LIMIT): List<ContentMatch> {
        val tsQuery = TsQuery.build(lexemes(rawQuery)) ?: return emptyList()

        return query(
            SEARCH_SQL,
            3,
            {
                bindString(0, tsQuery)
                bindString(1, workspaceId)
                bindLong(2, limit.toLong())
            }
        ) { cursor ->
            ContentMatch(
                documentId = cursor.getString(0)!!,
                stepId = cursor.getString(1)!!,
                type = cursor.getLong(2)!!.toInt(),
                text = cursor.getString(3) ?: "",
                position = cursor.getString(4)!!,
            )
        }
    }

    /**
     * The lexemes of [text] as the index holds them: what `to_tsvector('simple', ...)` makes of
     * it, in the order they appear. The parser keeps an email, a decimal, a version or a path
     * as one lexeme and drops the punctuation, so a search splits the way the steps were indexed.
     */
    fun lexemes(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return query(
            "SELECT lexeme FROM unnest(to_tsvector('simple', ?)) ORDER BY positions[1]",
            1,
            { bindString(0, text) }
        ) { cursor -> cursor.getString(0) ?: "" }
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

    /** A step whose text matched: enough to show a snippet and open the document at it. */
    data class ContentMatch(
        val documentId: String,
        val stepId: String,
        val type: Int,
        val text: String,
        val position: String,
    )

    companion object {
        const val INDEX = "idx_story_step_text_fts"
        const val DEFAULT_LIMIT = 20

        /** The tsvector of a step's text. The same expression in the index and in the query. */
        private const val VECTOR = "to_tsvector('simple', coalesce(s.text, ''))"

        /** The types of the steps searched: message, check item, unordered list item, code block. */
        private const val TEXT_TYPES = "(0, 10, 16, 23)"

        private val SEARCH_SQL = """
            WITH q AS (SELECT to_tsquery('simple', ?) AS tsq)
            SELECT m.document_id, m.id, m.type, m.text, m.position
            FROM (
              SELECT DISTINCT ON (s.document_id) s.document_id, s.id, s.type, s.text, s.position,
                     ts_rank($VECTOR, q.tsq) AS rank
              FROM story_step_entity s
              JOIN document_entity d ON d.id = s.document_id
              CROSS JOIN q
              WHERE d.workspace_id = ? AND d.deleted = FALSE
                AND s.type IN $TEXT_TYPES
                AND $VECTOR @@ q.tsq
              ORDER BY s.document_id, rank DESC, s.position
            ) m
            ORDER BY m.rank DESC
            LIMIT ?
        """.trimIndent()

        /** The schema, one statement per element. Kept in sync with init.sql and the production script. */
        fun ddl(): List<String> = listOf(
            "CREATE INDEX IF NOT EXISTS $INDEX ON story_step_entity USING GIN (to_tsvector('simple', coalesce(text, '')))",
        )
    }
}
