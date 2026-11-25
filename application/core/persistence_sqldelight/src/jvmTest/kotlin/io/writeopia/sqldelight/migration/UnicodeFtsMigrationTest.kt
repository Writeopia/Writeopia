package io.writeopia.sqldelight.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Migration 24 rebuilds the full-text index with unicode61 and only the text indexed. */
class UnicodeFtsMigrationTest {

    private val migration = File("src/commonMain/sqldelight/migration/24.sqm").readText()

    @Test
    fun `accented words fold their case and the id is not searched`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """
            CREATE TABLE storyStepEntity (
              id TEXT PRIMARY KEY, local_id TEXT NOT NULL, type INTEGER NOT NULL, parent_id TEXT, url TEXT,
              path TEXT, text TEXT, checked INTEGER NOT NULL, position TEXT NOT NULL, document_id TEXT NOT NULL,
              is_group INTEGER NOT NULL, has_inner_steps INTEGER NOT NULL, background_color INTEGER,
              tags TEXT NOT NULL, spans TEXT NOT NULL, link_to_document TEXT, last_updated_at INTEGER
            )
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            "CREATE VIRTUAL TABLE storyStepEntityFts USING fts4(text TEXT, id TEXT, document_id TEXT UNINDEXED, position TEXT UNINDEXED)",
            0
        )
        driver.execute(
            null,
            "INSERT INTO storyStepEntity VALUES ('abc', 'abc', 0, NULL, NULL, NULL, 'Ótimo resultado', 0, '0001000000', 'd1', 0, 0, NULL, '', '', NULL, 1)",
            0
        )

        runMigration(driver)

        assertEquals(listOf("abc"), matches(driver, "ótimo"))
        assertEquals(emptyList(), matches(driver, "abc"))

        // The update trigger follows a change of the text, and leaves the index alone otherwise.
        driver.execute(null, "UPDATE storyStepEntity SET url = 'x' WHERE id = 'abc'", 0)
        assertEquals(listOf("abc"), matches(driver, "ótimo"))
        driver.execute(null, "UPDATE storyStepEntity SET text = 'Péssimo resultado' WHERE id = 'abc'", 0)
        assertEquals(emptyList(), matches(driver, "ótimo"))
        assertEquals(listOf("abc"), matches(driver, "PÉSSIMO"))
    }

    private fun runMigration(driver: JdbcSqliteDriver) {
        migration.split(";").map(String::trim).filter(String::isNotEmpty)
            .map { statement -> statement.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n") }
            .fold(mutableListOf<String>()) { statements, piece ->
                val last = statements.lastOrNull()
                if (last != null && last.contains("BEGIN") && !last.contains("END")) {
                    statements[statements.lastIndex] = "$last;\n$piece"
                } else {
                    statements.add(piece)
                }
                statements
            }
            .map { statement -> if (statement.contains("BEGIN")) "$statement;" else statement }
            .forEach { statement -> driver.execute(null, statement, 0) }
    }

    private fun matches(driver: JdbcSqliteDriver, word: String): List<String> =
        driver.executeQuery(
            null,
            "SELECT id FROM storyStepEntityFts WHERE storyStepEntityFts MATCH '$word' ORDER BY id",
            { cursor ->
                val rows = mutableListOf<String>()
                while (cursor.next().value) rows.add(cursor.getString(0)!!)
                QueryResult.Value(rows.toList())
            },
            0
        ).value
}
