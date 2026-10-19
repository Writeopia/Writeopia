package io.writeopia.databse.tombstone

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * The steps deleted in a workspace, kept after the rows are gone so every device learns of the
 * deletion and none brings the step back.
 *
 * The table is partitioned by month of deletion (SQLDelight's PostgreSQL dialect has no
 * `PARTITION BY`, so it lives outside the generated schema, like the embeddings): a device
 * asks for the deletions since its last sync, which is a range scan over a few recent
 * partitions whatever the age of the table.
 *
 * The partitions come from pg_partman in production (WriteopiaScripts2,
 * `db-migrations/story_step_tombstones/2_partman.sql`: months made ahead plus a default
 * partition). Without it, locally and in tests, this store creates the month's partition on
 * first use. It never creates one while a partition already covers the month or the table has
 * a default partition, so the two never fight over a month.
 *
 * The parent table is created by `docker/postgres/init.sql` locally, by the production script
 * in WriteopiaScripts2 (`db-migrations/story_step_tombstones/1_schema.sql`), and by
 * [ensureSchema] in tests and debug mode. All three must agree with [ddl].
 */
class StoryStepTombstoneStore(private val driver: SqlDriver) {

    private val partitionsEnsured = mutableSetOf<YearMonth>()

    /** Creates the parent table if it doesn't exist. */
    fun ensureSchema() {
        ddl().forEach { statement -> driver.execute(null, statement, 0, null).value }
    }

    /** Records that [stepIds] of [documentId] were deleted at [deletedAt] (epoch milliseconds). */
    fun insert(stepIds: Collection<String>, documentId: String, workspaceId: String, deletedAt: Long) {
        insertAll(stepIds.map { stepId -> Tombstone(stepId, documentId, workspaceId, deletedAt) })
    }

    fun insertAll(tombstones: Collection<Tombstone>) {
        if (tombstones.isEmpty()) return

        tombstones.map { tombstone -> YearMonth.from(Instant.ofEpochMilli(tombstone.deletedAt).atOffset(ZoneOffset.UTC)) }
            .distinct()
            .forEach(::ensurePartition)

        tombstones.chunked(BATCH).forEach { batch ->
            val values = batch.joinToString(", ") { "(?, ?, ?, ?)" }
            driver.execute(
                null,
                "INSERT INTO $TABLE (step_id, document_id, workspace_id, deleted_at) VALUES $values ON CONFLICT DO NOTHING",
                batch.size * 4
            ) {
                batch.forEachIndexed { index, tombstone ->
                    bindString(index * 4, tombstone.stepId)
                    bindString(index * 4 + 1, tombstone.documentId)
                    bindString(index * 4 + 2, tombstone.workspaceId)
                    bindLong(index * 4 + 3, tombstone.deletedAt)
                }
            }.value
        }
    }

    /** The ids deleted in [workspaceId] after [since] (epoch milliseconds, exclusive). */
    fun deletedSince(workspaceId: String, since: Long): List<String> =
        query(
            "SELECT DISTINCT step_id FROM $TABLE WHERE workspace_id = ? AND deleted_at > ?",
            2,
            {
                bindString(0, workspaceId)
                bindLong(1, since)
            }
        ) { cursor -> cursor.getString(0)!! }

    /** The ids of [documentId] deleted after [since] (epoch milliseconds, exclusive). */
    fun deletedSinceForDocument(documentId: String, since: Long): List<String> =
        query(
            "SELECT DISTINCT step_id FROM $TABLE WHERE document_id = ? AND deleted_at > ?",
            2,
            {
                bindString(0, documentId)
                bindLong(1, since)
            }
        ) { cursor -> cursor.getString(0)!! }

    /** Which of [stepIds] were deleted at some point: a save must not bring them back. */
    fun tombstonedAmong(stepIds: Collection<String>): Set<String> {
        val ids = stepIds.distinct()
        if (ids.isEmpty()) return emptySet()

        return ids.chunked(BATCH).flatMap { batch ->
            query(
                "SELECT DISTINCT step_id FROM $TABLE WHERE step_id IN ${placeholders(batch.size)}",
                batch.size,
                { batch.forEachIndexed { index, id -> bindString(index, id) } }
            ) { cursor -> cursor.getString(0)!! }
        }.toSet()
    }

    /** Drops the tombstones of a workspace, when the workspace goes. */
    fun deleteByWorkspace(workspaceId: String) {
        driver.execute(null, "DELETE FROM $TABLE WHERE workspace_id = ?", 1) {
            bindString(0, workspaceId)
        }.value
    }

    /**
     * Makes sure a row of [month] has somewhere to go: a partition that covers the month, or
     * the default partition when something else (pg_partman) manages the set. Only when there
     * is neither is the month's partition created here. Two services may race to create the
     * same one: the loser's error is ignored once the month is covered.
     */
    @Synchronized
    private fun ensurePartition(month: YearMonth) {
        if (month in partitionsEnsured) return

        val from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        if (!isCovered(from, to)) {
            val statement =
                "CREATE TABLE IF NOT EXISTS ${partitionName(month)} PARTITION OF $TABLE FOR VALUES FROM ($from) TO ($to)"
            try {
                driver.execute(null, statement, 0, null).value
            } catch (e: Exception) {
                if (!isCovered(from, to)) throw e
            }
        }
        partitionsEnsured.add(month)
    }

    /** True when a partition holds the whole range, or a default partition catches what none does. */
    private fun isCovered(from: Long, to: Long): Boolean =
        partitionBounds().any { bound ->
            when (bound) {
                is PartitionBound.Default -> true
                is PartitionBound.Range -> bound.from <= from && bound.to >= to
            }
        }

    /** The bounds of every partition of the table, read from the catalog. */
    private fun partitionBounds(): List<PartitionBound> =
        query(
            "SELECT pg_get_expr(c.relpartbound, c.oid) FROM pg_inherits i " +
                "JOIN pg_class c ON c.oid = i.inhrelid WHERE i.inhparent = '$TABLE'::regclass",
            0,
            null
        ) { cursor -> cursor.getString(0) ?: "" }
            .mapNotNull(PartitionBound::parse)

    private sealed class PartitionBound {
        object Default : PartitionBound()

        data class Range(val from: Long, val to: Long) : PartitionBound()

        companion object {
            private val range = Regex("FOR VALUES FROM \\('?(MINVALUE|-?\\d+)'?\\) TO \\('?(MAXVALUE|-?\\d+)'?\\)")

            /** Parses what `pg_get_expr(relpartbound)` prints, e.g. `FOR VALUES FROM ('1') TO ('2')` or `DEFAULT`. */
            fun parse(expression: String): PartitionBound? {
                if (expression.trim() == "DEFAULT") return Default
                val match = range.find(expression) ?: return null
                val (lower, upper) = match.destructured
                return Range(
                    from = if (lower == "MINVALUE") Long.MIN_VALUE else lower.toLong(),
                    to = if (upper == "MAXVALUE") Long.MAX_VALUE else upper.toLong()
                )
            }
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

    private fun placeholders(count: Int): String =
        (1..count).joinToString(separator = ", ", prefix = "(", postfix = ")") { "?" }

    data class Tombstone(
        val stepId: String,
        val documentId: String,
        val workspaceId: String,
        val deletedAt: Long,
    )

    companion object {
        const val TABLE = "story_step_tombstone"

        private const val BATCH = 500

        fun partitionName(month: YearMonth): String =
            "${TABLE}_${month.year}_${month.monthValue.toString().padStart(2, '0')}"

        /** The schema, one statement per element. Kept in sync with init.sql and the production script. */
        fun ddl(): List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
              step_id       TEXT NOT NULL,
              document_id   TEXT NOT NULL,
              workspace_id  TEXT NOT NULL,
              deleted_at    BIGINT NOT NULL,
              PRIMARY KEY (step_id, deleted_at)
            ) PARTITION BY RANGE (deleted_at)
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_story_step_tombstone_workspace_deleted_at ON $TABLE (workspace_id, deleted_at)",
            "CREATE INDEX IF NOT EXISTS idx_story_step_tombstone_document_deleted_at ON $TABLE (document_id, deleted_at)",
        )
    }
}
