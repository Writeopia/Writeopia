package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.ai.HeaderImages
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The backend in memory: draws numbered pictures and keeps the approved ones. */
private class FakeHeaderImages(
    val saved: MutableList<String> = mutableListOf(),
    var failure: String? = null,
    var saveFailure: String? = null,
) : HeaderImages {
    val generatedFor = mutableListOf<Pair<String, String>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun generate(workspaceId: String, documentTitle: String, documentText: String): ResultData<String> {
        gate?.await()
        generatedFor.add(documentTitle to documentText)
        val failure = failure
        return if (failure != null) {
            ResultData.Error(Exception(failure))
        } else {
            ResultData.Complete("https://cdn.test/header-images/$workspaceId/${generatedFor.size}.png")
        }
    }

    override suspend fun images(workspaceId: String): ResultData<List<String>> = ResultData.Complete(saved.toList())

    override suspend fun save(workspaceId: String, imageUrl: String): ResultData<Unit> {
        saveFailure?.let { message -> return ResultData.Error(Exception(message)) }
        saved.add(0, imageUrl)
        return ResultData.Complete(Unit)
    }
}

class HeaderImagesControllerTest {

    private fun TestScope.controller(
        images: FakeHeaderImages?,
        approved: MutableList<String> = mutableListOf(),
        workspaceId: suspend () -> String? = { "workspace" },
    ) = HeaderImagesController(
        scope = backgroundScope,
        resolveHeaderImages = { images },
        workspaceId = workspaceId,
        documentTitle = { "Coastal Journal" },
        documentText = { "The lighthouse stood alone." },
        onApproved = approved::add,
    )

    @Test
    fun `opening the menu lists the pictures of the workspace`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages(saved = mutableListOf("https://cdn.test/a.png", "https://cdn.test/b.png"))
        val controller = controller(images)

        controller.load()

        assertTrue(controller.state.value.canGenerate)
        assertEquals(listOf("https://cdn.test/a.png", "https://cdn.test/b.png"), controller.state.value.images)
    }

    @Test
    fun `without the cloud there is no button to draw`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller(images = null)

        controller.load()

        assertFalse(controller.state.value.canGenerate)
    }

    @Test
    fun `a drawn picture waits for approval before going in the header`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages()
        val approved = mutableListOf<String>()
        val controller = controller(images, approved)

        controller.generate()

        assertEquals(listOf("Coastal Journal" to "The lighthouse stood alone."), images.generatedFor)
        assertEquals(
            HeaderImageGeneration.Candidate("https://cdn.test/header-images/workspace/1.png"),
            controller.state.value.generation
        )
        assertTrue(approved.isEmpty())
        assertTrue(images.saved.isEmpty())
    }

    @Test
    fun `trying another draws a new candidate`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages()
        val controller = controller(images)

        controller.generate()
        controller.generate()

        assertEquals(2, images.generatedFor.size)
        assertEquals(
            HeaderImageGeneration.Candidate("https://cdn.test/header-images/workspace/2.png"),
            controller.state.value.generation
        )
    }

    @Test
    fun `an approved picture goes in the header and joins the workspace`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages(saved = mutableListOf("https://cdn.test/old.png"))
        val approved = mutableListOf<String>()
        val controller = controller(images, approved)
        controller.load()

        controller.generate()
        controller.approve()

        val url = "https://cdn.test/header-images/workspace/1.png"
        assertEquals(listOf(url), approved)
        assertEquals(listOf(url, "https://cdn.test/old.png"), images.saved)
        assertEquals(listOf(url, "https://cdn.test/old.png"), controller.state.value.images)
        assertEquals(HeaderImageGeneration.Idle, controller.state.value.generation)
    }

    @Test
    fun `a discarded picture joins nothing`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages()
        val approved = mutableListOf<String>()
        val controller = controller(images, approved)

        controller.generate()
        controller.discard()

        assertEquals(HeaderImageGeneration.Idle, controller.state.value.generation)
        assertTrue(approved.isEmpty())
        assertTrue(images.saved.isEmpty())
    }

    @Test
    fun `discarding while drawing drops the answer`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages().apply { gate = CompletableDeferred() }
        val controller = controller(images)

        controller.generate()
        assertEquals(HeaderImageGeneration.Generating, controller.state.value.generation)

        controller.discard()
        images.gate?.complete(Unit)

        assertEquals(HeaderImageGeneration.Idle, controller.state.value.generation)
    }

    @Test
    fun `asking again while drawing draws only once`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages().apply { gate = CompletableDeferred() }
        val controller = controller(images)

        controller.generate()
        controller.generate()
        images.gate?.complete(Unit)

        assertEquals(1, images.generatedFor.size)
    }

    @Test
    fun `a failure says why`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages(failure = "Monthly AI quota exceeded")
        val controller = controller(images)

        controller.generate()

        assertEquals(HeaderImageGeneration.Failed("Monthly AI quota exceeded"), controller.state.value.generation)
    }

    @Test
    fun `a picture the backend did not keep stays a candidate, out of the header and the workspace`() =
        runTest(UnconfinedTestDispatcher()) {
            val images = FakeHeaderImages(saveFailure = "Service unavailable")
            val approved = mutableListOf<String>()
            val controller = controller(images, approved)
            controller.load()

            controller.generate()
            controller.approve()

            val url = "https://cdn.test/header-images/workspace/1.png"
            assertEquals(
                HeaderImageGeneration.Candidate(url, saveFailed = true, saveError = "Service unavailable"),
                controller.state.value.generation
            )
            assertTrue(approved.isEmpty())
            assertTrue(controller.state.value.images.isEmpty())

            // Opening the menu again doesn't bring it in either.
            controller.load()
            assertTrue(controller.state.value.images.isEmpty())

            // Approving again, once the backend answers, keeps it.
            images.saveFailure = null
            controller.approve()
            assertEquals(listOf(url), approved)
            assertEquals(listOf(url), controller.state.value.images)
            assertEquals(HeaderImageGeneration.Idle, controller.state.value.generation)
        }

    @Test
    fun `a failure finding the workspace ends the drawing`() = runTest(UnconfinedTestDispatcher()) {
        val images = FakeHeaderImages()
        val controller = controller(images, workspaceId = { throw IllegalStateException("Database closed") })

        controller.generate()

        assertEquals(HeaderImageGeneration.Failed("Database closed"), controller.state.value.generation)
        assertTrue(images.generatedFor.isEmpty())
    }
}
