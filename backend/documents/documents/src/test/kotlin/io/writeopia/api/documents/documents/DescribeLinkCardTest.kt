@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.api.documents.documents.repository.getStoryStepById
import io.writeopia.api.documents.documents.repository.getStoryStepDocumentId
import io.writeopia.api.documents.documents.repository.getStoryStepsAfterTime
import io.writeopia.api.documents.documents.repository.upsertStoryStep
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.request.LinkCardDescriptionRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

class DescribeLinkCardTest {

    private val db = configureTestPersistence()
    private val workspaceId = "workspace-link-cards"
    private val documentId = "doc-link-cards"

    private fun linkCard(id: String = "card-1", url: String? = "https://writeopia.io") =
        StoryStep(id = id, type = StoryTypes.LINK_CARD.type, url = url)

    private fun request(step: StoryStep, position: String = Rank.at(1), requestTimestamp: Long = 5_000L) =
        LinkCardDescriptionRequest(
            storyStep = step.toApi(position),
            position = position,
            requestTimestamp = requestTimestamp
        )

    @AfterTest
    fun tearDown() {
        db.storyStepEntityQueries.deleteByDocumentId(documentId)
        val ids = db.documentEntityQueries.selectAllIdsByWorkspaceId(workspaceId).executeAsList()
        if (ids.isNotEmpty()) db.storyStepEntityQueries.deleteByDocumentIds(ids)
        db.documentEntityQueries.hardDeleteByWorkspaceId(workspaceId)
    }

    @Test
    fun `saves the description in the card the sync delivered, where the sync put it`() = runBlocking {
        db.upsertStoryStep(linkCard(), position = Rank.at(3), documentId = documentId, lastUpdatedAt = 1_000L)

        val result = DocumentsService.describeLinkCard(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(linkCard(), position = Rank.at(1), requestTimestamp = 5_000L),
            describe = { url -> "The home of $url, a text editor." },
            writeopiaDb = db
        )

        val success = assertIs<DocumentsService.LinkCardDescriptionResult.Success>(result)
        assertEquals(Rank.at(3), success.position)
        assertEquals("The home of https://writeopia.io, a text editor.", success.storyStep.text)

        val (position, stored) = db.getStoryStepById("card-1")!!
        assertEquals(Rank.at(3), position)
        assertEquals("The home of https://writeopia.io, a text editor.", stored.text)
        assertEquals("https://writeopia.io", stored.url)
        assertEquals(StoryTypes.LINK_CARD.type.number, stored.type.number)
    }

    @Test
    fun `the described card is returned by a sync from the request time on`() = runBlocking {
        db.upsertStoryStep(linkCard(), position = Rank.at(1), documentId = documentId, lastUpdatedAt = 1_000L)

        DocumentsService.describeLinkCard(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(linkCard(), requestTimestamp = 5_000L),
            describe = { "A description." },
            writeopiaDb = db
        )

        val after = db.getStoryStepsAfterTime(documentId, afterTime = 4_999L)
        assertEquals(listOf("card-1"), after.map { (_, step) -> step.id })
        assertEquals(5_000L, after.single().second.lastUpdatedAt)
    }

    @Test
    fun `creates the card and the document when the sync did not deliver them yet`() = runBlocking {
        val result = DocumentsService.describeLinkCard(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(linkCard(id = "card-new"), position = Rank.at(2)),
            describe = { "Brand new." },
            writeopiaDb = db
        )

        val success = assertIs<DocumentsService.LinkCardDescriptionResult.Success>(result)
        assertEquals(Rank.at(2), success.position)

        val (position, stored) = db.getStoryStepById("card-new")!!
        assertEquals(Rank.at(2), position)
        assertEquals("Brand new.", stored.text)
        assertEquals("doc-link-cards", DocumentsService.getDocumentById(documentId, workspaceId, db)?.id)
    }

    @Test
    fun `refuses a step that is not a link card or has no address`() = runBlocking {
        val notACard = DocumentsService.describeLinkCard(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(StoryStep(type = StoryTypes.TEXT.type, url = "https://a.io")),
            describe = { "x" },
            writeopiaDb = db
        )
        assertIs<DocumentsService.LinkCardDescriptionResult.InvalidRequest>(notACard)

        val noAddress = DocumentsService.describeLinkCard(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(linkCard(url = "  ")),
            describe = { "x" },
            writeopiaDb = db
        )
        assertIs<DocumentsService.LinkCardDescriptionResult.InvalidRequest>(noAddress)
        assertNull(db.getStoryStepById("card-1"))
    }

    @Test
    fun `refuses an address that is not a web address without asking the AI`() = runBlocking {
        var asked = false
        val result = DocumentsService.describeLinkCard(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(linkCard(url = "javascript:alert(1)")),
            describe = { asked = true; "x" },
            writeopiaDb = db
        )

        assertIs<DocumentsService.LinkCardDescriptionResult.InvalidRequest>(result)
        assertFalse(asked)
        assertNull(db.getStoryStepById("card-1"))
    }

    @Test
    fun `refuses a card id that belongs to another document without asking the AI`() = runBlocking {
        val otherDocumentId = "doc-other"
        db.upsertStoryStep(linkCard(id = "card-elsewhere"), position = Rank.at(1), documentId = otherDocumentId, lastUpdatedAt = 1_000L)
        try {
            var asked = false
            val result = DocumentsService.describeLinkCard(
                documentId = documentId,
                workspaceId = workspaceId,
                request = request(linkCard(id = "card-elsewhere")),
                describe = { asked = true; "Stolen." },
                writeopiaDb = db
            )

            assertIs<DocumentsService.LinkCardDescriptionResult.InvalidRequest>(result)
            assertFalse(asked)
            val (_, stored) = db.getStoryStepById("card-elsewhere")!!
            assertNull(stored.text)
            assertEquals(otherDocumentId, db.getStoryStepDocumentId("card-elsewhere"))
        } finally {
            db.storyStepEntityQueries.deleteByDocumentId(otherDocumentId)
        }
    }

    @Test
    fun `an empty answer from the AI changes nothing`() = runBlocking {
        db.upsertStoryStep(linkCard(), position = Rank.at(1), documentId = documentId, lastUpdatedAt = 1_000L)

        val result = DocumentsService.describeLinkCard(
            documentId = documentId,
            workspaceId = workspaceId,
            request = request(linkCard()),
            describe = { null },
            writeopiaDb = db
        )

        assertIs<DocumentsService.LinkCardDescriptionResult.Error>(result)
        assertNull(db.getStoryStepById("card-1")!!.second.text)
    }
}
