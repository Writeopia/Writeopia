@file:OptIn(ExperimentalTime::class)

package io.writeopia.ui.manager

import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.UserRepository
import io.writeopia.ui.model.LinkCardCreation
import io.writeopia.ui.model.TextInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class)
class LinkCardStateManagerTest {

    private val userRepository: UserRepository = object : UserRepository {
        override suspend fun getUser(): WriteopiaUser = WriteopiaUser.disconnectedUser()

        override suspend fun getWorkspace(): Workspace = Workspace.disconnectedWorkspace()
    }

    private fun managerWith(vararg lines: String?): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
            userRepository = userRepository,
        )
        val content = buildMap {
            put(0.0, StoryStep(type = StoryTypes.TITLE.type, text = "Title"))
            lines.forEachIndexed { index, text ->
                put(index + 1.0, StoryStep(type = StoryTypes.TEXT.type, text = text))
            }
        }
        manager.loadDocument(
            Document(
                content = content,
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
    fun pastingALinkInAnEmptyLineCreatesALinkCard() = runTest {
        val manager = managerWith(null)
        val creations = mutableListOf<LinkCardCreation>()
        val job = launch { manager.linkCardCreated.collect { creations.add(it) } }
        advanceUntilIdle()

        manager.handleTextInput(
            TextInput(text = "https://writeopia.io/docs", start = 25, end = 25),
            position = 1.0,
            lineBreakByContent = true
        )
        advanceUntilIdle()

        val card = manager.currentStory.value.stories[1.0]!!
        assertEquals(StoryTypes.LINK_CARD.type, card.type)
        assertEquals("https://writeopia.io/docs", card.url)
        assertNull(card.text, "the description comes from the backend later")

        assertEquals(1, creations.size)
        assertEquals(card.id, creations.single().storyStep.id)
        assertEquals(1.0, creations.single().position)

        // The card can not be typed in: an empty line was added below it and has the cursor.
        val state = manager.currentStory.value
        assertEquals(3, state.stories.size)
        val below = state.stories.entries.first { (position, _) -> position > 1.0 }
        assertEquals(StoryTypes.TEXT.type, below.value.type)
        assertEquals("", below.value.text)
        assertEquals(below.key, state.focus)
        job.cancel()
    }

    @Test
    fun pastingALinkAboveATextLineMovesTheCursorToThatLine() = runTest {
        val manager = managerWith(null, "below")

        manager.handleTextInput(
            TextInput(text = "https://writeopia.io", start = 20, end = 20),
            position = 1.0,
            lineBreakByContent = true
        )
        advanceUntilIdle()

        val state = manager.currentStory.value
        assertEquals(StoryTypes.LINK_CARD.type, state.stories[1.0]!!.type)
        assertEquals(3, state.stories.size, "no line is added when a text line is already below")
        assertEquals("below", state.stories[2.0]!!.text)
        assertEquals(2.0, state.focus)
    }

    @Test
    fun pastingALinkInALineWithTextKeepsItAsText() = runTest {
        val manager = managerWith("See ")

        manager.handleTextInput(
            TextInput(text = "See https://writeopia.io", start = 24, end = 24),
            position = 1.0,
            lineBreakByContent = true
        )
        advanceUntilIdle()

        val step = manager.currentStory.value.stories[1.0]!!
        assertEquals(StoryTypes.TEXT.type, step.type)
        assertEquals("See https://writeopia.io", step.text)
    }

    @Test
    fun typingInAnEmptyLineKeepsItAsText() = runTest {
        val manager = managerWith(null)

        manager.handleTextInput(
            TextInput(text = "h", start = 1, end = 1),
            position = 1.0,
            lineBreakByContent = true
        )
        advanceUntilIdle()

        val step = manager.currentStory.value.stories[1.0]!!
        assertEquals(StoryTypes.TEXT.type, step.type)
        assertEquals("h", step.text)
    }

    @Test
    fun aServerStepIsAppliedWhereItsIdIsInTheDocument() = runTest {
        val manager = managerWith(null)
        manager.handleTextInput(
            TextInput(text = "https://writeopia.io", start = 20, end = 20),
            position = 1.0,
            lineBreakByContent = true
        )
        advanceUntilIdle()
        val card = manager.currentStory.value.stories[1.0]!!

        val applied = manager.applyServerStoryStep(card.copy(text = "A text editor for everyone."))
        advanceUntilIdle()

        assertTrue(applied)
        val described = manager.currentStory.value.stories[1.0]!!
        assertEquals(card.id, described.id)
        assertEquals(StoryTypes.LINK_CARD.type, described.type)
        assertEquals("A text editor for everyone.", described.text)
        assertEquals("https://writeopia.io", described.url)
    }

    @Test
    fun aServerStepForAnUnknownIdIsIgnored() = runTest {
        val manager = managerWith("hello")

        val applied = manager.applyServerStoryStep(
            StoryStep(id = "unknown", type = StoryTypes.LINK_CARD.type, url = "https://a.io", text = "x")
        )

        assertTrue(!applied)
        assertEquals("hello", manager.currentStory.value.stories[1.0]!!.text)
    }
}
