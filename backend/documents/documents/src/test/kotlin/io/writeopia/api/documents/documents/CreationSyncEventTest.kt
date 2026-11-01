@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.databse.HikariCp
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.request.LinkCardDescriptionRequest
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class CreationSyncEventTest {

    private val db = configureTestPersistence()
    private val workspaceId = "workspace-creation-events"
    private val userId = "user-creation-events"
    private val now = 1_792_000_000_000L

    private fun document(id: String) = Document(
        id = id,
        title = "Note",
        createdAt = Instant.fromEpochMilliseconds(now),
        lastUpdatedAt = Instant.fromEpochMilliseconds(now),
        lastSyncedAt = Instant.fromEpochMilliseconds(now),
        workspaceId = workspaceId,
        parentId = "root"
    )

    private fun events() =
        db.syncEventEntityQueries.selectByWorkspaceAfterTime(workspaceId, 0).executeAsList()

    @AfterTest
    fun tearDown() {
        val documentIds = db.documentEntityQueries.selectAllIdsByWorkspaceId(workspaceId).executeAsList()
        if (documentIds.isNotEmpty()) db.storyStepEntityQueries.deleteByDocumentIds(documentIds)
        db.documentEntityQueries.hardDeleteByWorkspaceId(workspaceId)
        // The sync_event table has no delete query: the events of a test must not leak into the next.
        HikariCp.driver()
            .execute(null, "DELETE FROM sync_event WHERE workspace_id = '$workspaceId'", 0, null)
            .value
    }

    @Test
    fun `a first upload of a document writes one creation event`() = runBlocking {
        DocumentsService.receiveDocuments(listOf(document("doc-1")), workspaceId, userId, db, useAi = false)

        val event = events().single()
        assertEquals("CREATE_DOCUMENT", event.event_type)
        assertEquals("doc-1", event.entity_id)
        assertEquals(userId, event.user_id)
        assertEquals(workspaceId, event.workspace_id)
    }

    @Test
    fun `a second upload of the same document writes no creation event`() = runBlocking {
        DocumentsService.receiveDocuments(listOf(document("doc-1")), workspaceId, userId, db, useAi = false)
        DocumentsService.receiveDocuments(listOf(document("doc-1")), workspaceId, userId, db, useAi = false)

        assertEquals(1, events().size)
    }

    @Test
    fun `upserting a new document writes a creation event`() = runBlocking {
        DocumentsService.upsertDocument(document("doc-2"), workspaceId, userId, db, embeddingService = null)

        val event = events().single()
        assertEquals("CREATE_DOCUMENT", event.event_type)
        assertEquals("doc-2", event.entity_id)
    }

    @Test
    fun `upserting an existing document writes no creation event`() = runBlocking {
        DocumentsService.upsertDocument(document("doc-2"), workspaceId, userId, db, embeddingService = null)
        DocumentsService.upsertDocument(document("doc-2"), workspaceId, userId, db, embeddingService = null)

        assertEquals(1, events().size)
    }

    @Test
    fun `cloning a document writes a creation event per clone`() = runBlocking {
        db.saveDocument(document("doc-3"))

        val clones = DocumentsService.cloneDocuments(listOf("doc-3"), workspaceId, userId, db, useAi = false)

        assertEquals(clones.single().id, events().single().entity_id)
    }

    @Test
    fun `a step sync that creates the document writes a creation event`() = runBlocking {
        DocumentsService.syncStorySteps(
            "doc-4",
            workspaceId,
            userId,
            StoryStepSyncRequest(
                documentId = "doc-4",
                workspaceId = workspaceId,
                lastSyncTimestamp = 0L,
                requestTimestamp = now,
                changes = emptyList(),
                deletions = emptyList()
            ),
            db
        )

        val event = events().single()
        assertEquals("CREATE_DOCUMENT", event.event_type)
        assertEquals("doc-4", event.entity_id)
    }

    @Test
    fun `a link card describe that creates the document writes a creation event`() = runBlocking {
        DocumentsService.describeLinkCard(
            "doc-5",
            workspaceId,
            userId,
            LinkCardDescriptionRequest(
                storyStep = StoryStep(
                    id = "card-5",
                    type = StoryTypes.LINK_CARD.type,
                    url = "https://writeopia.io"
                ).toApi(Rank.at(1)),
                position = Rank.at(1),
                requestTimestamp = now
            ),
            { "A description." },
            db
        )

        val event = events().single()
        assertEquals("CREATE_DOCUMENT", event.event_type)
        assertEquals("doc-5", event.entity_id)
    }
}
