package io.writeopia.account.viewmodel

import io.writeopia.ai.HeaderImages
import io.writeopia.ai.WorkspaceHeaderImage
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pictures of the backend in memory. */
private class FakeHeaderImages(
    val pictures: MutableList<WorkspaceHeaderImage> = mutableListOf(),
    var listFailure: Boolean = false,
    var deleteFailure: Boolean = false,
) : HeaderImages {
    val deleted = mutableListOf<Pair<String, String>>()

    /** Holds a listing back, after it read the pictures, until completed. */
    var listingGate: CompletableDeferred<Unit>? = null

    override suspend fun generate(
        workspaceId: String,
        documentTitle: String,
        documentText: String,
        instructions: String
    ): ResultData<String> = ResultData.Error()

    override suspend fun images(workspaceId: String): ResultData<List<String>> =
        ResultData.Complete(pictures.map { it.url })

    override suspend fun save(workspaceId: String, imageUrl: String): ResultData<Unit> = ResultData.Complete(Unit)

    override suspend fun pictures(workspaceId: String): ResultData<List<WorkspaceHeaderImage>> {
        if (listFailure) return ResultData.Error()
        val listed = pictures.toList()
        listingGate?.await()
        return ResultData.Complete(listed)
    }

    override suspend fun delete(workspaceId: String, imageId: String): ResultData<Unit> {
        if (deleteFailure) return ResultData.Error()
        deleted.add(workspaceId to imageId)
        pictures.removeAll { it.id == imageId }
        return ResultData.Complete(Unit)
    }
}

class WorkspaceHeaderImagesControllerTest {

    private fun picture(id: String) = WorkspaceHeaderImage(id, "https://cdn.test/header-images/ws/$id.png", 0)

    private fun TestScope.controller(source: HeaderImages?, workspaceId: String? = "ws") =
        WorkspaceHeaderImagesController(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            resolveHeaderImages = { source },
            workspaceId = { workspaceId },
        )

    @Test
    fun `the pictures of the workspace are listed`() = runTest {
        val source = FakeHeaderImages(mutableListOf(picture("a"), picture("b")))
        val controller = controller(source)

        controller.load()

        assertEquals(WorkspaceHeaderImagesState.Status.LOADED, controller.state.value.status)
        assertEquals(listOf("a", "b"), controller.state.value.images.map { it.id })
    }

    @Test
    fun `outside an online workspace there is nothing to manage`() = runTest {
        val controller = controller(source = null)

        controller.load()

        assertEquals(WorkspaceHeaderImagesState.Status.OFFLINE, controller.state.value.status)
    }

    @Test
    fun `a listing that fails can be retried`() = runTest {
        val source = FakeHeaderImages(mutableListOf(picture("a")), listFailure = true)
        val controller = controller(source)

        controller.load()
        assertEquals(WorkspaceHeaderImagesState.Status.FAILED, controller.state.value.status)

        source.listFailure = false
        controller.load()
        assertEquals(WorkspaceHeaderImagesState.Status.LOADED, controller.state.value.status)
        assertEquals(listOf("a"), controller.state.value.images.map { it.id })
    }

    @Test
    fun `a deleted picture leaves the list`() = runTest {
        val source = FakeHeaderImages(mutableListOf(picture("a"), picture("b")))
        val controller = controller(source)
        controller.load()

        controller.delete("a")

        assertEquals(listOf("ws" to "a"), source.deleted)
        assertEquals(listOf("b"), controller.state.value.images.map { it.id })
        assertTrue(controller.state.value.deleting.isEmpty())
    }

    @Test
    fun `a picture that could not be deleted stays, with the error`() = runTest {
        val source = FakeHeaderImages(mutableListOf(picture("a")), deleteFailure = true)
        val controller = controller(source)
        controller.load()

        controller.delete("a")

        assertEquals(listOf("a"), controller.state.value.images.map { it.id })
        assertTrue(controller.state.value.deleteFailed)
        assertTrue(controller.state.value.deleting.isEmpty())

        controller.dismissDeleteError()
        assertEquals(false, controller.state.value.deleteFailed)
    }

    @Test
    fun `a listing that started before a delete does not bring the picture back`() = runTest {
        val source = FakeHeaderImages(mutableListOf(picture("a"), picture("b")))
        val controller = controller(source)
        controller.load()

        val gate = CompletableDeferred<Unit>()
        source.listingGate = gate
        controller.load()
        controller.delete("a")
        gate.complete(Unit)

        assertEquals(listOf("b"), controller.state.value.images.map { it.id })
    }

    @Test
    fun `a source that throws leaves no picture dimmed`() = runTest {
        val controller = WorkspaceHeaderImagesController(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            resolveHeaderImages = { throw IllegalStateException("no session") },
            workspaceId = { "ws" },
        )

        controller.load()
        assertEquals(WorkspaceHeaderImagesState.Status.FAILED, controller.state.value.status)

        controller.delete("a")
        assertTrue(controller.state.value.deleting.isEmpty())
        assertTrue(controller.state.value.deleteFailed)
    }
}
