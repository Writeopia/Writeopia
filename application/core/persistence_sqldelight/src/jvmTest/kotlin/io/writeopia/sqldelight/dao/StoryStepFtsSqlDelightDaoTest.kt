package io.writeopia.sqldelight.dao

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sqldelight.database.DatabaseFactory.createDatabase
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The full-text index follows every write to the steps, through the triggers of the schema. */
@OptIn(ExperimentalTime::class)
class StoryStepFtsSqlDelightDaoTest {

    private val workspaceId = "ws"

    @Test
    fun `a saved document is found by the words inside it, with the step that holds them`() = runTest {
        val database = createDatabase(DriverFactory(), JdbcSqliteDriver.IN_MEMORY)
        val repository = SqlDelightDaoInjector(database).provideDocumentRepository()
        val dao = StoryStepFtsSqlDelightDao(database)

        repository.saveDocument(document("doc-a", "Groceries", "a1" to "buy some fresh bananas", "a2" to "and milk"))
        repository.saveDocument(document("doc-b", "Plans", "b1" to "nothing relevant here"))

        val match = dao.searchContent("BANANA", workspaceId).single()
        assertEquals("doc-a", match.documentId)
        assertEquals("Groceries", match.title)
        assertEquals("buy some fresh bananas", match.text)
        assertEquals(Rank.at(1), match.position)
        assertEquals(setOf(Rank.at(1)), dao.searchInDocument("banana", "doc-a"))
    }

    @Test
    fun `an edited step is found by its new words only, a deleted one not at all`() = runTest {
        val database = createDatabase(DriverFactory(), JdbcSqliteDriver.IN_MEMORY)
        val repository = SqlDelightDaoInjector(database).provideDocumentRepository()
        val dao = StoryStepFtsSqlDelightDao(database)

        val saved = document("doc-a", "Groceries", "a1" to "buy some fresh bananas")
        repository.saveDocument(saved)
        repository.saveDocument(
            saved.copy(content = saved.content.mapValues { (_, step) -> if (step.id == "a1") step.copy(text = "buy some fresh apples") else step })
        )

        assertTrue(dao.searchContent("banana", workspaceId).isEmpty())
        assertEquals(listOf("doc-a"), dao.searchContent("apple", workspaceId).map { match -> match.documentId })

        database.storyStepEntityQueries.deleteByDocumentId("doc-a")
        assertTrue(dao.searchContent("apple", workspaceId).isEmpty())
    }

    @Test
    fun `every word is needed, other workspaces and the title step stay out`() = runTest {
        val database = createDatabase(DriverFactory(), JdbcSqliteDriver.IN_MEMORY)
        val repository = SqlDelightDaoInjector(database).provideDocumentRepository()
        val dao = StoryStepFtsSqlDelightDao(database)

        repository.saveDocument(document("doc-a", "Kiwi", "a1" to "the quarterly report was approved"))
        repository.saveDocument(document("doc-other", "Other", "o1" to "quarterly report").copy(workspaceId = "other"))

        assertEquals(listOf("doc-a"), dao.searchContent("quarterly report", workspaceId).map { match -> match.documentId })
        assertTrue(dao.searchContent("quarterly missing", workspaceId).isEmpty())
        assertTrue(dao.searchContent("kiwi", workspaceId).isEmpty())
        assertTrue(dao.searchContent("!!!", workspaceId).isEmpty())
    }

    @Test
    fun `letters of any alphabet fold their case, and any sign separates the words`() = runTest {
        val database = createDatabase(DriverFactory(), JdbcSqliteDriver.IN_MEMORY)
        val repository = SqlDelightDaoInjector(database).provideDocumentRepository()
        val dao = StoryStepFtsSqlDelightDao(database)

        repository.saveDocument(document("doc-a", "Notas", "a1" to "Ótimo resultado da reunião", "a2" to "hello\u2014world"))

        assertEquals(listOf("doc-a"), dao.searchContent("ótimo", workspaceId).map { match -> match.documentId })
        assertEquals(listOf("doc-a"), dao.searchContent("REUNIÃO", workspaceId).map { match -> match.documentId })
        assertEquals(listOf("doc-a"), dao.searchContent("world", workspaceId).map { match -> match.documentId })
        // The id of a step is not a word of its text.
        assertTrue(dao.searchContent("a1", workspaceId).isEmpty())
    }

    private fun document(id: String, title: String, vararg steps: Pair<String, String>): Document {
        val content = listOf(StoryStep(id = "$id-title", type = StoryTypes.TITLE.type, text = title)) +
            steps.map { (stepId, text) -> StoryStep(id = stepId, type = StoryTypes.TEXT.type, text = text) }
        return Document(
            id = id,
            title = title,
            createdAt = Instant.fromEpochMilliseconds(1_000),
            lastUpdatedAt = Instant.fromEpochMilliseconds(2_000),
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "root",
            content = content.mapIndexed { index, step -> Rank.at(index) to step }.toMap()
        )
    }
}
