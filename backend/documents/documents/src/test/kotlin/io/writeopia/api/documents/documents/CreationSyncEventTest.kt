@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.databse.HikariCp
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.request.DocumentSyncInfo
import io.writeopia.sdk.serialization.request.LinkCardDescriptionRequest
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun `an upload of a deleted document writes no creation event`() = runBlocking {
        DocumentsService.receiveDocuments(
            listOf(document("doc-6").copy(deleted = true)),
            workspaceId,
            userId,
            db,
            useAi = false
        )

        assertTrue(events().none { event -> event.entity_id == "doc-6" })
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

    @Test
    fun `a generated summary document writes a creation event`() = runBlocking {
        db.saveDocument(
            document("doc-6").copy(
                content = mapOf(
                    Rank.at(0) to StoryStep(id = "text-6", type = StoryTypes.TEXT.type, text = "Some text.")
                )
            )
        )
        val genAi = mockk<GenAiService> {
            every { isAvailable() } returns true
            coEvery { generateSummary(any(), any()) } returns AiGenerateResponse(response = "A summary.")
        }

        val result = DocumentsService.generateSummaryDocument(
            documents = listOf(DocumentSyncInfo(documentId = "doc-6", lastSyncedAt = null)),
            targetFolderId = "root",
            workspaceId = workspaceId,
            userId = userId,
            summaryTitle = null,
            model = null,
            ignoreSyncCheck = true,
            genAiService = genAi,
            writeopiaDb = db
        )

        val summary = assertIs<DocumentsService.GenerateSummaryResult.Success>(result).document
        val event = events().single()
        assertEquals("CREATE_DOCUMENT", event.event_type)
        assertEquals(summary.id, event.entity_id)
        assertEquals(userId, event.user_id)
    }

    @Test
    fun `a failing creation event takes the document write down with it`() = runBlocking {
        try {
            sql("DROP TRIGGER IF EXISTS fail_sync_event ON sync_event")
            sql(
                "CREATE OR REPLACE FUNCTION fail_sync_event() RETURNS trigger AS " +
                    "'BEGIN RAISE EXCEPTION ''sync event insert failed''; END' LANGUAGE plpgsql"
            )
            sql(
                "CREATE TRIGGER fail_sync_event BEFORE INSERT ON sync_event " +
                    "FOR EACH ROW EXECUTE FUNCTION fail_sync_event()"
            )

            assertFails {
                DocumentsService.receiveDocuments(
                    listOf(document("doc-7")),
                    workspaceId,
                    userId,
                    db,
                    useAi = false
                )
            }
            assertNull(db.getDocumentById("doc-7", workspaceId))
        } finally {
            sql("DROP TRIGGER IF EXISTS fail_sync_event ON sync_event")
            sql("DROP FUNCTION IF EXISTS fail_sync_event()")
        }
    }

    private fun sql(statement: String) = HikariCp.driver().execute(null, statement, 0, null).value
}
