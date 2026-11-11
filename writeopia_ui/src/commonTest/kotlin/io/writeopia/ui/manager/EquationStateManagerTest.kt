@file:OptIn(ExperimentalTime::class)

package io.writeopia.ui.manager

import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.UserRepository
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ui.model.EquationEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class)
class EquationStateManagerTest {

    private val userRepository: UserRepository = object : UserRepository {
        override suspend fun getUser(): WriteopiaUser = WriteopiaUser.disconnectedUser()

        override suspend fun getWorkspace(): Workspace = Workspace.disconnectedWorkspace()
    }

    private val drawingJson = """{"id":"d","strokes":[],"width":10,"height":10}"""

    private fun manager(): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
            userRepository = userRepository,
        )
        manager.loadDocument(
            Document(
                content = mapOf(
                    Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
                    Rank.at(1) to StoryStep(id = "drawing", type = StoryTypes.DRAWING.type, text = drawingJson),
                    Rank.at(2) to StoryStep(id = "equation", type = StoryTypes.EQUATION.type, text = "x^2", url = "https://cdn/x.png"),
                ),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )
        return manager
    }

    @Test
    fun settingAnEquationReplacesTheDrawingInPlaceAndUndoBringsItBack() = runTest {
        val manager = manager()

        manager.setEquation(Rank.at(1), "\\frac{1}{2}", "https://cdn/half.png")

        val step = manager.currentStory.value.stories[Rank.at(1)]!!
        assertEquals("drawing", step.id)
        assertEquals(StoryTypes.EQUATION.type, step.type)
        assertEquals("\\frac{1}{2}", step.text)
        assertEquals("https://cdn/half.png", step.url)

        manager.undo()

        val restored = manager.currentStory.value.stories[Rank.at(1)]!!
        assertEquals(StoryTypes.DRAWING.type, restored.type)
        assertEquals(drawingJson, restored.text)
        assertNull(restored.url)
    }

    @Test
    fun thePencilOfAnEquationAsksTheAppToEditIt() = runTest {
        val manager = manager()
        val events = mutableListOf<EquationEvent>()
        val job = launch { manager.equationEvents.collect { events.add(it) } }
        advanceUntilIdle()

        manager.onEquationEdition(Rank.at(2))
        advanceUntilIdle()

        val event = assertIs<EquationEvent.Edit>(events.single())
        assertEquals(Rank.at(2), event.position)
        assertEquals("x^2", event.step.text)
        // The step is still an equation: nothing changed until the app answers.
        assertEquals(StoryTypes.EQUATION.type, manager.currentStory.value.stories[Rank.at(2)]!!.type)
        job.cancel()
    }
}
