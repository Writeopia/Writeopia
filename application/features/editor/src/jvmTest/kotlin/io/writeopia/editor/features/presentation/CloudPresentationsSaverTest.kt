@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.presentation

import io.writeopia.core.presentations.PresentationException
import io.writeopia.core.presentations.PresentationGenerator
import io.writeopia.core.presentations.PresentationsRepository
import io.writeopia.core.presentations.PresentationsStore
import io.writeopia.editor.features.presentation.repository.CloudPresentationsSaver
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.presentation.Slide
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private class MemoryStore : PresentationsStore {
    val saved = mutableMapOf<String, Presentation>()

    override suspend fun presentations(documentId: String, workspaceId: String) =
        ResultData.Complete(saved.values.filter { it.documentId == documentId })

    override suspend fun presentation(id: String, workspaceId: String) = ResultData.Complete(saved[id])

    override suspend fun deletePresentation(id: String, workspaceId: String): ResultData<Unit> {
        saved.remove(id)
        return ResultData.Complete(Unit)
    }

    override suspend fun savePresentation(presentation: Presentation): ResultData<Unit> {
        saved[presentation.id] = presentation
        return ResultData.Complete(Unit)
    }
}

/** The backend: answers with [presentations], or fails like when it can't be reached. */
private class FakeCloud(var presentations: List<Presentation> = emptyList(), var offline: Boolean = false) :
    PresentationsRepository, PresentationGenerator {
    private fun <T> fail(): ResultData<T> = ResultData.Error(PresentationException("offline"))

    override suspend fun presentations(documentId: String, workspaceId: String): ResultData<List<Presentation>> =
        if (offline) fail() else ResultData.Complete(presentations.filter { it.documentId == documentId })

    override suspend fun presentation(id: String, workspaceId: String): ResultData<Presentation?> =
        if (offline) fail() else ResultData.Complete(presentations.firstOrNull { it.id == id })

    override suspend fun deletePresentation(id: String, workspaceId: String): ResultData<Unit> =
        if (offline) fail() else ResultData.Complete(Unit)

    override suspend fun generatePresentation(documentId: String, workspaceId: String): ResultData<Presentation> =
        if (offline) fail() else ResultData.Complete(sample("generated", documentId))
}

private fun sample(id: String, documentId: String = "d1") = Presentation(
    id = id,
    documentId = documentId,
    workspaceId = "w1",
    title = "Sky $id",
    createdAt = Instant.fromEpochMilliseconds(1),
    slides = listOf(Slide("Sky"))
)

class CloudPresentationsSaverTest {

    @Test
    fun `what the cloud generates, lists and opens is kept on the device`() = runTest {
        val cloud = FakeCloud(presentations = listOf(sample("listed"), sample("opened", "d2")))
        val store = MemoryStore()
        val repository = CloudPresentationsSaver(cloud, store)

        repository.generatePresentation("d1", "w1")
        repository.presentations("d1", "w1")
        repository.presentation("opened", "w1")

        assertEquals(setOf("generated", "listed", "opened"), store.saved.keys)
    }

    @Test
    fun `offline the presentations come from the device`() = runTest {
        val cloud = FakeCloud(presentations = listOf(sample("p1")))
        val store = MemoryStore()
        val repository = CloudPresentationsSaver(cloud, store)
        repository.presentations("d1", "w1")

        cloud.offline = true

        assertEquals(listOf("p1"), (repository.presentations("d1", "w1") as ResultData.Complete).data.map { it.id })
        assertEquals("p1", (repository.presentation("p1", "w1") as ResultData.Complete).data?.id)
        assertIs<ResultData.Error<*>>(repository.presentation("unknown", "w1"))
        assertIs<ResultData.Error<*>>(repository.presentations("other-document", "w1"))
    }

    @Test
    fun `deleting removes the copy only when the cloud deleted it`() = runTest {
        val cloud = FakeCloud(presentations = listOf(sample("p1"), sample("p2")))
        val store = MemoryStore()
        val repository = CloudPresentationsSaver(cloud, store)
        repository.presentations("d1", "w1")

        repository.deletePresentation("p1", "w1")
        cloud.offline = true
        repository.deletePresentation("p2", "w1")

        assertTrue("p1" !in store.saved)
        assertTrue("p2" in store.saved)
    }
}
