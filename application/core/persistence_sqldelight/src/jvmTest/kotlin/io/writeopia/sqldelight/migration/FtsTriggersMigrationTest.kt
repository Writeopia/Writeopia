package io.writeopia.sqldelight.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 23 rebuilds the full-text index from the steps and adds the triggers that keep it
 * complete from then on.
 */
class FtsTriggersMigrationTest {

    private val migration = File("src/commonMain/sqldelight/migration/23.sqm").readText()

    @Test
    fun `the index is rebuilt from the steps and follows the writes after`() {
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
        insertStep(driver, "a", "0001000000", "bananas for breakfast")
        insertStep(driver, "b", "0001000001", "milk")
        // A stale row: the step was edited after being indexed, and a row of a step long gone.
        driver.execute(null, "INSERT INTO storyStepEntityFts VALUES ('old bananas text', 'b', 'd1', '0001000001')", 0)
        driver.execute(null, "INSERT INTO storyStepEntityFts VALUES ('bananas gone', 'gone', 'd1', '0001000002')", 0)

        migration.split(";").map(String::trim).filter(String::isNotEmpty)
            .map { statement -> statement.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n") }
            // The triggers hold ';' inside BEGIN ... END: glue the pieces of each one back together.
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

        assertEquals(listOf("a"), matches(driver, "bananas"))

        // An upsert that replaces the text re-indexes the step once.
        driver.execute(
            null,
            "INSERT INTO storyStepEntity VALUES ('a', 'a', 0, NULL, NULL, NULL, 'apples for breakfast', 0, '0001000000', 'd1', 0, 0, NULL, '', '', NULL, 1) " +
                "ON CONFLICT(id) DO UPDATE SET text = excluded.text",
            0
        )
        assertEquals(emptyList(), matches(driver, "bananas"))
        assertEquals(listOf("a"), matches(driver, "apples"))
        assertEquals(listOf("a"), matches(driver, "breakfast"))

        insertStep(driver, "c", "0001000002", "cherries")
        assertEquals(listOf("c"), matches(driver, "cherries"))

        driver.execute(null, "DELETE FROM storyStepEntity WHERE id = 'c'", 0)
        assertEquals(emptyList(), matches(driver, "cherries"))
    }

    private fun insertStep(driver: JdbcSqliteDriver, id: String, position: String, text: String) {
        driver.execute(
            null,
            "INSERT INTO storyStepEntity VALUES ('$id', '$id', 0, NULL, NULL, NULL, '$text', 0, '$position', 'd1', 0, 0, NULL, '', '', NULL, 1)",
            0
        )
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
