@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.persistence.core.repository.InMemoryDocumentRepository
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.DeletedStoryStepsResponse
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentSyncServiceTest {

    private val workspaceId = "workspace"
    private val repository = InMemoryDocumentRepository()
    private val documentsApi = mockk<DocumentsApi>()
    private val requests = mutableListOf<StoryStepSyncRequest>()
    private var answer: (StoryStepSyncRequest) -> StoryStepSyncResponse = { request ->
        StoryStepSyncResponse(serverTimestamp = request.requestTimestamp + 1, updatedSteps = emptyList(), deletedIds = emptyList())
    }

    init {
        coEvery { documentsApi.sendDocuments(any(), workspaceId) } returns ResultData.Complete(Unit)
    }

    private val service = DocumentSyncService(
        documentRepository = repository,
        syncApi = { request ->
            requests.add(request)
            answer(request)
        },
        documentsApi = documentsApi,
    )

    @Test
    fun `the favorites become the backend's, wherever the documents are`() = runBlocking {
        val favoriteHere = storeDocument(step("a", 10)).copy(favorite = true).also { repository.saveDocumentMetadata(it) }
        val favoriteElsewhere = storeDocument(step("b", 10))
        coEvery { documentsApi.getUserFavorites(workspaceId) } returns ResultData.Complete(listOf(favoriteElsewhere.id))

        assertTrue(service.receiveFavorites(workspaceId))

        assertFalse(repository.loadDocumentById(favoriteHere.id, workspaceId)!!.favorite)
        assertTrue(repository.loadDocumentById(favoriteElsewhere.id, workspaceId)!!.favorite)
    }

    @Test
    fun `the favorites stay when the backend can't be asked`() = runBlocking {
        val favoriteHere = storeDocument(step("a", 10)).copy(favorite = true).also { repository.saveDocumentMetadata(it) }
        coEvery { documentsApi.getUserFavorites(workspaceId) } returns ResultData.Error()

        assertFalse(service.receiveFavorites(workspaceId))

        assertTrue(repository.loadDocumentById(favoriteHere.id, workspaceId)!!.favorite)
    }

    @Test
    fun `the deletions are sent as ids and forgotten once the backend has them`() = runBlocking {
        val document = storeDocument(step("a", 10), step("b", 10))
        repository.deleteStoryStep("a", document.id)
        coEvery { documentsApi.deleteStorySteps(any(), workspaceId) } returns ResultData.Complete(Unit)

        assertEquals(setOf("a"), service.sendDeletedSteps(workspaceId))

        coVerify {
            documentsApi.deleteStorySteps(
                match { it.single().stepId == "a" && it.single().documentId == document.id },
                workspaceId
            )
        }
        assertTrue(repository.loadStoryStepTombstones(workspaceId).isEmpty())
    }

    @Test
    fun `the deletions stay pending while the backend refuses them`() = runBlocking {
        val document = storeDocument(step("a", 10))
        repository.deleteStoryStep("a", document.id)
        coEvery { documentsApi.deleteStorySteps(any(), workspaceId) } returns ResultData.Error()

        assertNull(service.sendDeletedSteps(workspaceId))

        assertEquals(listOf("a"), repository.loadStoryStepTombstones(workspaceId).map { it.stepId })
    }

    @Test
    fun `only the steps edited since the last send go, and they are stamped with the send`() = runBlocking {
        val document = storeDocument(step("old", 500), step("edited", 2_000), lastSyncedAt = 1_000)
        val before = Clock.System.now().toEpochMilliseconds()

        assertEquals(listOf(document.id), service.syncDocuments(listOf(document), workspaceId)?.map { it.id })

        assertEquals(listOf("edited"), requests.single().changes.map { it.storyStep.id })
        val stored = repository.loadDocumentById(document.id, workspaceId)!!
        val sentAt = stored.lastSyncedAt!!.toEpochMilliseconds()
        assertTrue(sentAt >= before)
        assertEquals(sentAt, stored.step("edited").lastUpdatedAt)
        assertEquals(500L, stored.step("old").lastUpdatedAt)
        // Nothing is newer than the stamp: the next sync sends nothing.
        requests.clear()
        assertEquals(listOf(stored.id), service.syncDocuments(listOf(stored), workspaceId)?.map { it.id })
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `the metadata goes without content after the steps, and nothing is stamped until it is there`() = runBlocking {
        val document = storeDocument(step("a", 2_000, "edited"), lastSyncedAt = 1_000, lastUpdatedAt = 3_000)
        coEvery { documentsApi.sendDocuments(any(), workspaceId) } returns ResultData.Error()

        assertNull(service.syncDocuments(listOf(document), workspaceId))

        // The steps went, the metadata didn't: still outdated, with its steps, for the next sync.
        assertEquals(1, requests.size)
        val stillOutdated = repository.loadDocumentById(document.id, workspaceId)!!
        assertEquals(1_000L, stillOutdated.lastSyncedAt!!.toEpochMilliseconds())
        assertEquals(2_000L, stillOutdated.step("a").lastUpdatedAt)

        coEvery { documentsApi.sendDocuments(any(), workspaceId) } returns ResultData.Complete(Unit)
        assertEquals(listOf(document.id), service.syncDocuments(listOf(stillOutdated), workspaceId)?.map { it.id })

        coVerify { documentsApi.sendDocuments(match { it.single().id == document.id && it.single().content.isEmpty() }, workspaceId) }
        val synced = repository.loadDocumentById(document.id, workspaceId)!!
        assertTrue(synced.lastSyncedAt!! >= synced.lastUpdatedAt)
        assertEquals(synced.lastSyncedAt!!.toEpochMilliseconds(), synced.step("a").lastUpdatedAt)
    }

    @Test
    fun `a document edited while it was being sent stays outdated`() = runBlocking {
        val document = storeDocument(step("a", 2_000), lastSyncedAt = 1_000, lastUpdatedAt = 2_000)
        coEvery { documentsApi.sendDocuments(any(), workspaceId) } coAnswers {
            // The editor saved again during the send.
            repository.saveDocumentMetadata(document.copy(title = "Edited meanwhile", lastUpdatedAt = Clock.System.now() + 1.seconds))
            ResultData.Complete(Unit)
        }

        service.syncDocuments(listOf(document), workspaceId)

        val stored = repository.loadDocumentById(document.id, workspaceId)!!
        assertEquals("Edited meanwhile", stored.title)
        assertTrue(stored.lastUpdatedAt > stored.lastSyncedAt!!)
    }

    @Test
    fun `a document whose steps could not be sent is left out, the others go on`() = runBlocking {
        val failing = storeDocument(step("x", 5), lastSyncedAt = 1)
        val fine = storeDocument(step("y", 5), lastSyncedAt = 1)
        answer = { request ->
            if (request.documentId == failing.id) throw IllegalStateException("offline for this one")
            StoryStepSyncResponse(request.requestTimestamp + 1, emptyList(), emptyList())
        }

        assertEquals(listOf(fine.id), service.syncDocuments(listOf(failing, fine), workspaceId)?.map { it.id })

        coVerify { documentsApi.sendDocuments(match { it.map { doc -> doc.id } == listOf(fine.id) }, workspaceId) }
        assertEquals(1L, repository.loadDocumentById(failing.id, workspaceId)!!.lastSyncedAt!!.toEpochMilliseconds())
    }

    @Test
    fun `a document never sent goes whole, in batches`() = runBlocking {
        val steps = (1..120).map { index -> step("s$index", index.toLong()) }
        val document = storeDocument(*steps.toTypedArray(), lastSyncedAt = null)

        assertEquals(listOf(document.id), service.syncDocuments(listOf(document), workspaceId)?.map { it.id })

        assertEquals(listOf(50, 50, 20), requests.map { it.changes.size })
        assertEquals(steps.map { it.id }, requests.flatMap { request -> request.changes.map { it.storyStep.id } })
    }

    @Test
    fun `a step edited during the send stays newer than the stamp`() = runBlocking {
        val document = storeDocument(step("a", 2_000), lastSyncedAt = 1_000)
        answer = { request ->
            // The editor saved the step again while the request was in flight.
            runBlocking {
                repository.saveStoryStep(step("a", request.requestTimestamp + 5, "edited again"), 0.0, document.id)
            }
            StoryStepSyncResponse(request.requestTimestamp + 1, emptyList(), emptyList())
        }

        assertEquals(listOf(document.id), service.syncDocuments(listOf(document), workspaceId)?.map { it.id })

        val stored = repository.loadDocumentById(document.id, workspaceId)!!
        assertTrue(stored.step("a").lastUpdatedAt!! > stored.lastSyncedAt!!.toEpochMilliseconds())
    }

    @Test
    fun `what the backend answers with is applied, newer steps and deletions, never a deleted step`() = runBlocking {
        val document = storeDocument(step("mine", 5_000, "mine"), step("theirs", 100, "old"), step("gone", 100), lastSyncedAt = 1_000)
        repository.deleteStoryStep("deleted-here", document.id)
        answer = { request ->
            StoryStepSyncResponse(
                serverTimestamp = request.requestTimestamp + 1,
                updatedSteps = listOf(
                    step("mine", 200, "older elsewhere").toApi(0.0),
                    step("theirs", 9_000, "new").toApi(1.0),
                    step("added", 9_000, "added").toApi(3.0),
                    step("deleted-here", 9_000, "back?").toApi(4.0),
                ),
                deletedIds = listOf("gone")
            )
        }

        assertEquals(listOf(document.id), service.syncDocuments(listOf(document), workspaceId)?.map { it.id })

        val stored = repository.loadDocumentById(document.id, workspaceId)!!
        assertEquals("mine", stored.step("mine").text)
        assertEquals("new", stored.step("theirs").text)
        assertEquals("added", stored.step("added").text)
        assertNull(stored.stepOrNull("gone"))
        assertNull(stored.stepOrNull("deleted-here"))
    }

    @Test
    fun `a received document is merged step by step, the newer copy of each one wins`() = runBlocking {
        val local = storeDocument(step("a", 5, "local a"), step("b", 1, "local b"), lastSyncedAt = 10, lastUpdatedAt = 10)
        val received = local.copy(
            title = "Renamed elsewhere",
            lastUpdatedAt = Instant.fromEpochMilliseconds(20),
            lastSyncedAt = Instant.fromEpochMilliseconds(20),
            content = mapOf(
                0.0 to step("a", 3, "server a"),
                1.0 to step("b", 9, "server b"),
                2.0 to step("c", 9, "server c"),
            )
        )

        assertTrue(service.mergeReceivedDocument(received))

        val stored = repository.loadDocumentById(local.id, workspaceId)!!
        assertEquals("local a", stored.step("a").text)
        assertEquals("server b", stored.step("b").text)
        assertEquals("server c", stored.step("c").text)
        assertEquals("Renamed elsewhere", stored.title)
        // In sync: nothing was pending here.
        assertTrue(stored.lastSyncedAt!! >= stored.lastUpdatedAt)
    }

    @Test
    fun `a received document never brings back a step deleted here`() = runBlocking {
        val local = storeDocument(step("a", 5), step("b", 5), lastSyncedAt = 10, lastUpdatedAt = 10)
        repository.deleteStoryStep("b", local.id)
        val received = local.copy(lastUpdatedAt = Instant.fromEpochMilliseconds(20))

        service.mergeReceivedDocument(received)

        assertNull(repository.loadDocumentById(local.id, workspaceId)!!.stepOrNull("b"))
    }

    @Test
    fun `a received document that is still outdated here keeps looking outdated`() = runBlocking {
        val local = storeDocument(step("a", 150, "edited here"), lastSyncedAt = 100, lastUpdatedAt = 200)
        val received = local.copy(
            title = "Renamed elsewhere",
            lastUpdatedAt = Instant.fromEpochMilliseconds(300),
            lastSyncedAt = Instant.fromEpochMilliseconds(300),
            content = mapOf(0.0 to step("a", 50, "older elsewhere"))
        )

        service.mergeReceivedDocument(received)

        val stored = repository.loadDocumentById(local.id, workspaceId)!!
        assertEquals("Renamed elsewhere", stored.title)
        assertEquals("edited here", stored.step("a").text)
        assertEquals(200L, stored.lastUpdatedAt.toEpochMilliseconds())
        assertEquals(100L, stored.lastSyncedAt!!.toEpochMilliseconds())
        assertEquals(listOf(stored.id), repository.loadOutdatedDocumentsForWorkspace(workspaceId).map { it.id })
    }

    @Test
    fun `a received document unknown here is saved, a document deleted here is not brought back`() = runBlocking {
        val unknown = document("unknown", step("a", 1))
        assertTrue(service.mergeReceivedDocument(unknown))
        assertNotNull(repository.loadDocumentById("unknown", workspaceId))

        val deleted = storeDocument(step("a", 1)).copy(deleted = true)
        repository.saveDocument(deleted)
        assertFalse(service.mergeReceivedDocument(deleted.copy(deleted = false, title = "Back?")))
        assertTrue(repository.loadDocumentById(deleted.id, workspaceId)!!.deleted)
    }

    @Test
    fun `a received listing never brings back a step deleted in this sync`() = runBlocking {
        val local = storeDocument(step("a", 5), lastSyncedAt = 10, lastUpdatedAt = 10)
        val received = local.copy(
            lastUpdatedAt = Instant.fromEpochMilliseconds(20),
            content = mapOf(0.0 to step("a", 5), 1.0 to step("stale", 20))
        )

        service.mergeReceivedDocument(received, excluding = setOf("stale"))

        assertNull(repository.loadDocumentById(local.id, workspaceId)!!.stepOrNull("stale"))
    }

    @Test
    fun `the steps deleted elsewhere are removed without a tombstone`() = runBlocking {
        val document = storeDocument(step("a", 1), step("b", 1))
        coEvery { documentsApi.getDeletedStoryStepIds(workspaceId, any()) } returns
            ResultData.Complete(DeletedStoryStepsResponse(stepIds = listOf("b"), serverTimestamp = 778))

        val received = service.receiveDeletedSteps(workspaceId, Instant.DISTANT_PAST)
        assertEquals(setOf("b"), received?.stepIds)
        assertEquals(778L, received?.serverTimestamp?.toEpochMilliseconds())

        assertNull(repository.loadDocumentById(document.id, workspaceId)!!.stepOrNull("b"))
        assertTrue(repository.loadStoryStepTombstones(workspaceId).isEmpty())
    }

    private fun step(id: String, lastUpdatedAt: Long, text: String = id) =
        StoryStep(id = id, type = StoryTypes.TEXT.type, text = text, lastUpdatedAt = lastUpdatedAt)

    private fun document(id: String, vararg steps: StoryStep, lastSyncedAt: Long? = null, lastUpdatedAt: Long = 0) =
        Document(
            id = id,
            title = "Doc",
            content = steps.mapIndexed { index, step -> index.toDouble() to step }.toMap(),
            createdAt = Instant.fromEpochMilliseconds(0),
            lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
            lastSyncedAt = lastSyncedAt?.let(Instant::fromEpochMilliseconds),
            workspaceId = workspaceId,
            parentId = "root"
        )

    private var storedCount = 0

    private suspend fun storeDocument(vararg steps: StoryStep, lastSyncedAt: Long? = null, lastUpdatedAt: Long = 0): Document =
        document("doc-${storedCount++}", *steps, lastSyncedAt = lastSyncedAt, lastUpdatedAt = lastUpdatedAt)
            .also { repository.saveDocument(it) }

    private fun Document.stepOrNull(id: String): StoryStep? = content.values.firstOrNull { step -> step.id == id }

    private fun Document.step(id: String): StoryStep = assertNotNull(stepOrNull(id), "step $id")
}
