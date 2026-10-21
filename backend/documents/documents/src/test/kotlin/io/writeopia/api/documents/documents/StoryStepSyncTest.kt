@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.api.documents.documents.repository.getStoryStepsAfterTime
import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
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

class StoryStepSyncTest {

    private val db = configureTestPersistence()
    private val workspaceId = "workspace-step-sync"
    private val documentId = "doc-step-sync"

    /** A document edited on a client at [editedAt], epoch milliseconds. */
    private val editedAt = 1_791_998_156_814L

    private fun request(step: StoryStep, requestTimestamp: Long, lastSync: Long = 0L) =
        StoryStepSyncRequest(
            documentId = documentId,
            workspaceId = workspaceId,
            lastSyncTimestamp = lastSync,
            requestTimestamp = requestTimestamp,
            changes = listOf(StoryStepChangeApi(storyStep = step.toApi(1L), position = 1L)),
            deletions = emptyList()
        )

    private fun storeDocument(lastUpdatedAt: Long, lastSyncedAt: Long) {
        db.saveDocument(
            Document(
                id = documentId,
                title = "Plan",
                createdAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
                lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
                lastSyncedAt = Instant.fromEpochMilliseconds(lastSyncedAt),
                workspaceId = workspaceId,
                parentId = "root",
                content = mapOf(0L to StoryStep(id = "title", type = StoryTypes.TITLE.type, text = "Plan"))
            )
        )
    }

    @AfterTest
    fun tearDown() {
        db.storyStepEntityQueries.deleteByDocumentId(documentId)
        db.documentEntityQueries.hardDeleteByWorkspaceId(workspaceId)
    }

    @Test
    fun `a step sync marks the document row as changed and synced`() = runBlocking {
        storeDocument(lastUpdatedAt = editedAt - 60_000, lastSyncedAt = editedAt - 60_000)
        val before = System.currentTimeMillis()

        DocumentsService.syncStorySteps(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(StoryStep(id = "line", type = StoryTypes.TEXT.type, text = "New line"), editedAt),
            writeopiaDb = db
        )

        val document = db.getDocumentById(documentId, workspaceId)!!
        assertEquals(editedAt, document.lastUpdatedAt.toEpochMilliseconds())
        // Server time: a folder diff from before this sync now lists the document.
        assertTrue(document.lastSyncedAt!!.toEpochMilliseconds() >= before)
    }

    @Test
    fun `a step sync never moves the document back in time`() = runBlocking {
        storeDocument(lastUpdatedAt = editedAt + 5_000, lastSyncedAt = editedAt)

        DocumentsService.syncStorySteps(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(StoryStep(id = "line", type = StoryTypes.TEXT.type, text = "Older"), editedAt),
            writeopiaDb = db
        )

        assertEquals(editedAt + 5_000, db.getDocumentById(documentId, workspaceId)!!.lastUpdatedAt.toEpochMilliseconds())
    }

    @Test
    fun `a sync without changes leaves the document row alone`() = runBlocking {
        storeDocument(lastUpdatedAt = editedAt, lastSyncedAt = editedAt)

        DocumentsService.syncStorySteps(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(StoryStep(id = "line", type = StoryTypes.TEXT.type), editedAt).copy(changes = emptyList()),
            writeopiaDb = db
        )

        assertEquals(editedAt, db.getDocumentById(documentId, workspaceId)!!.lastSyncedAt!!.toEpochMilliseconds())
    }

    @Test
    fun `step timestamps keep their 64 bits`() = runBlocking {
        storeDocument(lastUpdatedAt = editedAt, lastSyncedAt = editedAt)

        DocumentsService.syncStorySteps(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(StoryStep(id = "line", type = StoryTypes.TEXT.type, text = "Line"), editedAt),
            writeopiaDb = db
        )

        val steps = db.getStoryStepsAfterTime(documentId, editedAt - 1)
        assertEquals(listOf("line"), steps.map { (_, step) -> step.id })
        assertEquals(editedAt, steps.single().second.lastUpdatedAt)
        assertTrue(db.getStoryStepsAfterTime(documentId, editedAt).isEmpty())
    }
}
