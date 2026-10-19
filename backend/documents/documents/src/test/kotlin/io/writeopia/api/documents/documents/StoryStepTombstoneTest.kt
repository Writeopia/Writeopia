@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.api.documents.documents.repository.getDocumentWithContentById
import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.api.documents.documents.repository.storyStepTombstones
import io.writeopia.databse.HikariCp
import io.writeopia.databse.tombstone.StoryStepTombstoneStore
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.data.StoryStepTombstoneApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.request.StoryStepChangeApi
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class StoryStepTombstoneTest {

    private val db = configureTestPersistence()
    private val workspaceId = "workspace-tombstones"
    private val documentId = "doc-tombstones"
    private val editedAt = 1_791_998_156_814L

    @AfterTest
    fun tearDown() {
        db.storyStepEntityQueries.deleteByDocumentId(documentId)
        db.documentEntityQueries.hardDeleteByWorkspaceId(workspaceId)
        db.storyStepTombstones().deleteByWorkspace(workspaceId)
    }

    @Test
    fun `deleting steps keeps tombstones the other devices can read`() = runBlocking {
        storeDocument(step("a"), step("b"))
        val before = System.currentTimeMillis()

        DocumentsService.deleteStorySteps(listOf(StoryStepTombstoneApi("a", documentId)), workspaceId, db)

        assertEquals(listOf("title", "b"), stepIds())
        assertEquals(listOf("a"), DocumentsService.deletedStoryStepIds(workspaceId, before - 1, db))
        assertTrue(DocumentsService.deletedStoryStepIds(workspaceId, System.currentTimeMillis() + 1, db).isEmpty())
        // The document row changed, so the folder diffs of the other devices list it.
        assertTrue(db.getDocumentWithContentById(documentId, workspaceId)!!.lastSyncedAt!!.toEpochMilliseconds() >= before)
    }

    @Test
    fun `a deleted step never comes back, through a document save or a step sync`() = runBlocking {
        val document = storeDocument(step("a"), step("b"))
        DocumentsService.deleteStorySteps(listOf(StoryStepTombstoneApi("a", documentId)), workspaceId, db)

        db.saveDocument(document.copy(content = document.content.mapValues { (_, step) -> step.copy(lastUpdatedAt = editedAt + 1) }))
        assertEquals(listOf("title", "b"), stepIds())

        DocumentsService.syncStorySteps(documentId, workspaceId, request(listOf(step("a", editedAt + 2))), db)
        assertEquals(listOf("title", "b"), stepIds())
    }

    @Test
    fun `a step sync answers with the steps deleted elsewhere, and leaves tombstones of its own`() = runBlocking {
        storeDocument(step("a"), step("b"))
        DocumentsService.deleteStorySteps(listOf(StoryStepTombstoneApi("a", documentId)), workspaceId, db)

        // The client last synced when "b" was last edited, so its deletion of "b" stands.
        val response = DocumentsService.syncStorySteps(
            documentId,
            workspaceId,
            request(changes = emptyList(), deletions = listOf("b"), lastSync = editedAt),
            db
        )

        assertEquals(listOf("a"), response.deletedIds)
        assertEquals(listOf("title"), stepIds())
        assertEquals(setOf("a", "b"), DocumentsService.deletedStoryStepIds(workspaceId, editedAt, db).toSet())
    }

    @Test
    fun `a document save keeps the steps it doesn't carry and the newer copies of the ones it does`() = runBlocking {
        val document = storeDocument(step("a", editedAt, "a"), step("b", editedAt, "b"))

        db.saveDocument(document.copy(content = mapOf(0.0 to step("a", editedAt - 1, "older a"))))
        var stored = db.getDocumentWithContentById(documentId, workspaceId)!!
        assertEquals("a", stored.text("a"))
        assertEquals("b", stored.text("b"))

        db.saveDocument(document.copy(content = mapOf(0.0 to step("a", editedAt + 1, "newer a"))))
        stored = db.getDocumentWithContentById(documentId, workspaceId)!!
        assertEquals("newer a", stored.text("a"))
        assertEquals("b", stored.text("b"))
    }

    @Test
    fun `the folder diff carries the edit time of every step`() = runBlocking {
        storeDocument(step("a", editedAt))

        val documents = DocumentsService.getDocumentsDiffByFolder("root", workspaceId, 0L, "last_updated_at", db)

        assertEquals(editedAt, documents.single().content.values.first { step -> step.id == "a" }.lastUpdatedAt)
    }

    @Test
    fun `a partition made by someone else is used, never fought over`() = runBlocking {
        // What pg_partman does in production: the month's partition exists under its own name.
        // A month ahead of the ones the other tests touch, so their partitions don't overlap it.
        val month = java.time.YearMonth.now(java.time.ZoneOffset.UTC).plusMonths(6)
        val from = month.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val foreign = "story_step_tombstone_p${month.year}_${month.monthValue}_made_elsewhere"
        HikariCp.driver().execute(
            null,
            "CREATE TABLE IF NOT EXISTS $foreign PARTITION OF story_step_tombstone FOR VALUES FROM ($from) TO ($to)",
            0,
            null
        ).value
        storeDocument(step("a"))

        // A fresh store knows nothing of the partitions yet.
        val store = StoryStepTombstoneStore(HikariCp.driver())
        store.insert(listOf("a"), documentId, workspaceId, from + 1_000)

        assertEquals(listOf("a"), store.deletedSince(workspaceId, from))
    }

    @Test
    fun `deletions of steps outside the workspace are ignored`() = runBlocking {
        storeDocument(step("a"))

        DocumentsService.deleteStorySteps(listOf(StoryStepTombstoneApi("a", documentId)), "another-workspace", db)

        assertEquals(listOf("title", "a"), stepIds())
        assertTrue(DocumentsService.deletedStoryStepIds("another-workspace", 0L, db).isEmpty())
    }

    private fun step(id: String, lastUpdatedAt: Long = editedAt, text: String = id) =
        StoryStep(id = id, type = StoryTypes.TEXT.type, text = text, lastUpdatedAt = lastUpdatedAt)

    private fun request(
        changes: List<StoryStep>,
        deletions: List<String> = emptyList(),
        lastSync: Long = 0L
    ) = StoryStepSyncRequest(
        documentId = documentId,
        workspaceId = workspaceId,
        lastSyncTimestamp = lastSync,
        requestTimestamp = editedAt + 10,
        changes = changes.mapIndexed { index, step -> StoryStepChangeApi(step.toApi(index + 1.0), index + 1.0) },
        deletions = deletions
    )

    private fun storeDocument(vararg steps: StoryStep): Document {
        val content = listOf(StoryStep(id = "title", type = StoryTypes.TITLE.type, text = "Plan", lastUpdatedAt = editedAt)) + steps
        val document = Document(
            id = documentId,
            title = "Plan",
            createdAt = Instant.fromEpochMilliseconds(editedAt),
            lastUpdatedAt = Instant.fromEpochMilliseconds(editedAt),
            lastSyncedAt = Instant.fromEpochMilliseconds(editedAt),
            workspaceId = workspaceId,
            parentId = "root",
            content = content.mapIndexed { index, step -> index.toDouble() to step }.toMap()
        )
        db.saveDocument(document)
        return document
    }

    private suspend fun stepIds(): List<String> =
        db.getDocumentWithContentById(documentId, workspaceId)!!.content.values.map { step -> step.id }

    private fun Document.text(stepId: String): String? = content.values.first { step -> step.id == stepId }.text
}
