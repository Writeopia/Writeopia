package io.writeopia.sqldelight.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sdk.utils.iterables.Rank
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 21 turns the numeric positions of the steps into ranks: the steps of each document,
 * children among their siblings, get the ranks of 0, 1, 2... in their order.
 */
class RankMigrationTest {

    private val migration = File("src/commonMain/sqldelight/migration/21.sqm").readText()

    @Test
    fun `numeric positions become ranks in their order`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """
            CREATE TABLE storyStepEntity (
              id TEXT PRIMARY KEY, local_id TEXT NOT NULL, type INTEGER NOT NULL, parent_id TEXT, url TEXT,
              path TEXT, text TEXT, checked INTEGER NOT NULL, position INTEGER NOT NULL, document_id TEXT NOT NULL,
              is_group INTEGER NOT NULL, has_inner_steps INTEGER NOT NULL, background_color INTEGER,
              tags TEXT NOT NULL, spans TEXT NOT NULL, link_to_document TEXT, last_updated_at INTEGER
            )
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            "CREATE VIRTUAL TABLE storyStepEntityFts USING fts4(text TEXT, id TEXT, document_id TEXT UNINDEXED, position INTEGER UNINDEXED)",
            0
        )
        listOf(
            Triple("c", "d1", 20000),
            Triple("t", "d1", 0),
            Triple("b", "d1", 15000),
            Triple("a", "d1", 10000),
            Triple("x", "d2", 30000),
            Triple("w", "d2", 0),
        ).forEach { (id, document, position) ->
            driver.execute(
                null,
                "INSERT INTO storyStepEntity VALUES ('$id', '$id', 0, NULL, NULL, NULL, 'text $id', 0, $position, '$document', 0, 0, NULL, '', '', NULL, 1)",
                0
            )
        }
        driver.execute(
            null,
            "INSERT INTO storyStepEntity VALUES ('g1', 'g1', 0, 'b', NULL, NULL, 'child', 0, 5, 'd1', 0, 0, NULL, '', '', NULL, 1)",
            0
        )

        migration.split(";").map(String::trim).filter(String::isNotEmpty).forEach { statement ->
            driver.execute(null, statement.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n"), 0)
        }

        assertEquals(
            listOf(
                "d1" to "t" to Rank.at(0),
                "d1" to "a" to Rank.at(1),
                "d1" to "b" to Rank.at(2),
                "d1" to "c" to Rank.at(3),
                "d1" to "g1" to Rank.at(0),
                "d2" to "w" to Rank.at(0),
                "d2" to "x" to Rank.at(1),
            ),
            rows(driver, "SELECT document_id, id, position FROM storyStepEntity ORDER BY document_id, parent_id IS NOT NULL, position")
        )
        assertEquals(
            listOf("d1" to "a" to Rank.at(1)),
            rows(driver, "SELECT document_id, id, position FROM storyStepEntityFts WHERE storyStepEntityFts MATCH 'a'")
        )
    }

    private fun rows(driver: JdbcSqliteDriver, sql: String): List<Pair<Pair<String, String>, String>> =
        driver.executeQuery(
            null,
            sql,
            { cursor ->
                val rows = mutableListOf<Pair<Pair<String, String>, String>>()
                while (cursor.next().value) rows.add(cursor.getString(0)!! to cursor.getString(1)!! to cursor.getString(2)!!)
                QueryResult.Value(rows.toList())
            },
            0
        ).value
}
