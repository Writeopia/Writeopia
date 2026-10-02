@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.presentation

import io.mockk.coEvery
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.presentations.PresentationException
import io.writeopia.core.presentations.PresentationGenerator
import io.writeopia.core.presentations.PresentationsStore
import io.writeopia.editor.features.presentation.viewmodel.PresentationsController
import io.writeopia.editor.features.presentation.viewmodel.PresentationsSource
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.presentation.Slide
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Keeps presentations in memory and makes a fixed one, standing for the backend or the local AI. */
private class FakePresentations(var answer: Presentation? = null, var failure: String? = null) : PresentationsStore, PresentationGenerator {
    val saved = mutableListOf<Presentation>()
    val generatedFor = mutableListOf<String>()

    override suspend fun presentations(documentId: String, workspaceId: String) =
        ResultData.Complete(
            saved.filter {
                it.documentId == documentId && it.workspaceId == workspaceId
            }.sortedByDescending { it.createdAt }
        )

    override suspend fun presentation(id: String, workspaceId: String) = ResultData.Complete(saved.firstOrNull { it.id == id })

    override suspend fun deletePresentation(id: String, workspaceId: String): ResultData<Unit> {
        saved.removeAll { it.id == id }
        return ResultData.Complete(Unit)
    }

    override suspend fun savePresentation(presentation: Presentation): ResultData<Unit> {
        saved.removeAll { it.id == presentation.id }
        saved.add(presentation)
        return ResultData.Complete(Unit)
    }

    override suspend fun generatePresentation(documentId: String, workspaceId: String): ResultData<Presentation> {
        generatedFor.add(documentId)
        failure?.let { return ResultData.Error(PresentationException(it)) }
        val presentation = answer ?: return ResultData.Error(PresentationException("nothing"))
        savePresentation(presentation)
        return ResultData.Complete(presentation)
    }
}

class PresentationsControllerTest {

    private fun presentation(workspaceId: String) = Presentation(
        documentId = "d1",
        workspaceId = workspaceId,
        title = "Sky",
        createdAt = Instant.fromEpochMilliseconds(1),
        slides = listOf(Slide("Sky"), Slide("Bye"))
    )

    private fun auth(workspace: Workspace?, tier: Tier): AuthRepository = mockk(relaxed = true) {
        coEvery { getWorkspace() } returns workspace
        coEvery { getUser() } returns WriteopiaUser(id = "u1", email = "u@w.io", name = "U", tier = tier)
    }

    private val online = Workspace(
        id = "w1",
        userId = "u1",
        name = "Team",
        lastSync = Instant.fromEpochMilliseconds(0),
        selected = true,
        role = "ADMIN"
    )

    @Test
    fun `an online premium workspace uses the cloud`() = runTest(UnconfinedTestDispatcher()) {
        val cloud = FakePresentations(answer = presentation("w1"))
        val local = FakePresentations()
        val controller = PresentationsController(
            scope = this,
            documentId = { "d1" },
            authRepository = auth(online, Tier.PREMIUM),
            cloud = { PresentationsSource.Cloud(cloud, cloud) },
            local = { PresentationsSource.Local(local, local) },
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        assertTrue(controller.isAvailable.value)
        controller.openDialog()
        assertTrue(controller.showDialog.value)
        controller.generate()

        assertEquals(listOf("d1"), cloud.generatedFor)
        assertTrue(local.generatedFor.isEmpty())
        assertEquals("Sky", controller.generated.value?.title)
        assertEquals(listOf("Sky"), controller.presentations.value.map { it.title })
        assertFalse(controller.isGenerating.value)
        assertNull(controller.error.value)
    }

    @Test
    fun `an online workspace without premium asks for premium instead of a dialog`() = runTest(UnconfinedTestDispatcher()) {
        val cloud = FakePresentations()
        val controller = PresentationsController(
            scope = this,
            documentId = { "d1" },
            authRepository = auth(online, Tier.FREE),
            cloud = { PresentationsSource.Cloud(cloud, cloud) },
            local = null,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        assertTrue(controller.isAvailable.value, "the button shows, the dialog explains")
        controller.openDialog()

        assertTrue(controller.premiumRequested.value)
        assertFalse(controller.showDialog.value)
    }

    @Test
    fun `the offline workspace uses the local AI and stays hidden without a model`() = runTest(UnconfinedTestDispatcher()) {
        val local = FakePresentations(answer = presentation(Workspace.disconnectedWorkspace().id))
        val withModel = PresentationsController(
            scope = this,
            documentId = { "d1" },
            authRepository = auth(Workspace.disconnectedWorkspace(), Tier.FREE),
            cloud = { error("the cloud isn't used offline") },
            local = { PresentationsSource.Local(local, local) },
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        assertTrue(withModel.isAvailable.value)
        withModel.generate()
        assertEquals(listOf("d1"), local.generatedFor)
        assertEquals(1, local.saved.size)

        val withoutModel = PresentationsController(
            scope = this,
            documentId = { "d1" },
            authRepository = auth(Workspace.disconnectedWorkspace(), Tier.FREE),
            cloud = { error("the cloud isn't used offline") },
            local = { null },
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        assertFalse(withoutModel.isAvailable.value)
    }

    @Test
    fun `errors are shown and a presentation can be deleted`() = runTest(UnconfinedTestDispatcher()) {
        val cloud = FakePresentations(failure = "AI didn't return any slide")
        cloud.saved.add(presentation("w1"))
        val controller = PresentationsController(
            scope = this,
            documentId = { "d1" },
            authRepository = auth(online, Tier.PREMIUM),
            cloud = { PresentationsSource.Cloud(cloud, cloud) },
            local = null,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        controller.load()
        assertEquals(1, controller.presentations.value.size)

        controller.generate()
        assertEquals("AI didn't return any slide", controller.error.value)
        assertNull(controller.generated.value)

        controller.delete(controller.presentations.value.single())
        assertTrue(controller.presentations.value.isEmpty())
        assertTrue(cloud.saved.isEmpty())
    }
}
