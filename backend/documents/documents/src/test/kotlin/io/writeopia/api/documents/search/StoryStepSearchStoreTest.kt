@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.search

import io.writeopia.api.documents.documents.configureTestPersistence
import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.api.documents.documents.repository.storyStepSearch
import io.writeopia.databse.HikariCp
import io.writeopia.databse.search.StoryStepSearchStore
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class StoryStepSearchStoreTest {

    private val db = configureTestPersistence()
    private val workspaceId = "workspace-search"
    private val otherWorkspaceId = "workspace-search-other"
    private val editedAt = 1_791_998_156_814L
    private val stored = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        if (stored.isNotEmpty()) db.storyStepEntityQueries.deleteByDocumentIds(stored)
        db.documentEntityQueries.hardDeleteByWorkspaceId(workspaceId)
        db.documentEntityQueries.hardDeleteByWorkspaceId(otherWorkspaceId)
    }

    @Test
    fun `the schema can be created again`() {
        StoryStepSearchStore(HikariCp.driver()).ensureSchema()
        StoryStepSearchStore(HikariCp.driver()).ensureSchema()
    }

    @Test
    fun `a word inside a document finds it, with the matching step`() = runBlocking {
        storeDocument("doc-a", "Groceries", step("a1", "buy some fresh bananas"), step("a2", "and milk"))
        storeDocument("doc-b", "Plans", step("b1", "nothing relevant here"))

        val matches = db.storyStepSearch().search(workspaceId, "banana")

        val match = matches.single()
        assertEquals("doc-a", match.documentId)
        assertEquals("a1", match.stepId)
        assertEquals("buy some fresh bananas", match.text)
        assertEquals(Rank.at(1), match.position)
        assertEquals(StoryTypes.TEXT.type.number, match.type)
    }

    @Test
    fun `every kind of prose step counts, the title step doesn't`() = runBlocking {
        storeDocument("doc-check", "A", step("c", "remember the kiwi", StoryTypes.CHECK_ITEM))
        storeDocument("doc-list", "B", step("l", "kiwi for the salad", StoryTypes.UNORDERED_LIST_ITEM))
        storeDocument("doc-code", "C", step("k", "val kiwi = 1", StoryTypes.CODE_BLOCK))
        storeDocument("doc-title", "Kiwi", step("t", "unrelated text"))

        val found = ids(db.storyStepSearch().search(workspaceId, "kiwi"))

        assertEquals(setOf("doc-check", "doc-list", "doc-code"), found)
    }

    @Test
    fun `the search is case-insensitive, by prefix, and needs every word`() = runBlocking {
        storeDocument("doc-a", "A", step("a1", "The Quarterly Report was approved"))
        storeDocument("doc-b", "B", step("b1", "quarterly numbers only"))

        assertEquals(setOf("doc-a", "doc-b"), ids(db.storyStepSearch().search(workspaceId, "QUART")))
        assertEquals(setOf("doc-a"), ids(db.storyStepSearch().search(workspaceId, "quarterly report")))
        assertEquals(setOf("doc-a"), ids(db.storyStepSearch().search(workspaceId, "appro")))
        assertTrue(db.storyStepSearch().search(workspaceId, "quarterly missing").isEmpty())
    }

    @Test
    fun `one match per document, and deleted or foreign documents stay out`() = runBlocking {
        storeDocument("doc-a", "A", step("a1", "apple pie"), step("a2", "apple juice"), step("a3", "apple tart"))
        storeDocument("doc-deleted", "D", step("d1", "apple crumble"))
        storeDocument("doc-foreign", "F", step("f1", "apple strudel"), workspace = otherWorkspaceId)
        db.documentEntityQueries.delete(editedAt + 1, "doc-deleted")

        val matches = db.storyStepSearch().search(workspaceId, "apple")

        assertEquals(listOf("doc-a"), matches.map { match -> match.documentId })
        assertEquals(setOf("doc-foreign"), ids(db.storyStepSearch().search(otherWorkspaceId, "apple")))
    }

    @Test
    fun `the limit caps the documents, and a query without words finds nothing`() = runBlocking {
        (1..5).forEach { index -> storeDocument("doc-$index", "D$index", step("s$index", "pear number $index")) }

        assertEquals(2, db.storyStepSearch().search(workspaceId, "pear", limit = 2).size)
        assertTrue(db.storyStepSearch().search(workspaceId, "!!! ***").isEmpty())
        // Operators typed by the user are dropped, never passed to Postgres.
        assertEquals(5, db.storyStepSearch().search(workspaceId, "pear:* & 'number\"").size)
    }

    @Test
    fun `the words are split the way the index splits them`() = runBlocking {
        storeDocument("doc-mail", "A", step("m", "write to ana@example.com today"))
        storeDocument("doc-pi", "B", step("p", "pi is 3.14 more or less"))
        storeDocument("doc-path", "C", step("f", "see /usr/local/bin/tool"))
        storeDocument("doc-accent", "D", step("x", "Ótimo resultado"))

        val store = db.storyStepSearch()
        assertEquals(listOf("ana@example.com", "today"), store.lexemes("ana@example.com today"))
        assertEquals(setOf("doc-mail"), ids(store.search(workspaceId, "ana@example.com")))
        // The parser keeps an email whole: a piece of one is two words, which the index doesn't hold.
        assertTrue(store.search(workspaceId, "ana@exam").isEmpty())
        assertEquals(setOf("doc-pi"), ids(store.search(workspaceId, "3.14")))
        assertEquals(setOf("doc-path"), ids(store.search(workspaceId, "/usr/local/bin/tool")))
        assertEquals(setOf("doc-accent"), ids(store.search(workspaceId, "ótimo")))
        assertTrue(store.search(workspaceId, "it's \\ 'quoted'").isEmpty())
    }

    private fun ids(matches: List<StoryStepSearchStore.ContentMatch>) =
        matches.map { match -> match.documentId }.toSet()

    private fun step(id: String, text: String, type: StoryTypes = StoryTypes.TEXT) =
        StoryStep(id = id, type = type.type, text = text, lastUpdatedAt = editedAt)

    private fun storeDocument(
        id: String,
        title: String,
        vararg steps: StoryStep,
        workspace: String = workspaceId
    ): Document {
        val content = listOf(StoryStep(id = "$id-title", type = StoryTypes.TITLE.type, text = title, lastUpdatedAt = editedAt)) + steps
        val document = Document(
            id = id,
            title = title,
            createdAt = Instant.fromEpochMilliseconds(editedAt),
            lastUpdatedAt = Instant.fromEpochMilliseconds(editedAt),
            lastSyncedAt = Instant.fromEpochMilliseconds(editedAt),
            workspaceId = workspace,
            parentId = "root",
            content = content.mapIndexed { index, step -> Rank.at(index) to step }.toMap()
        )
        db.saveDocument(document)
        stored += id
        return document
    }
}
