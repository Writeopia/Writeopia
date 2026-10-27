@file:OptIn(ExperimentalTime::class)

package io.writeopia.ui.manager

import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.UserRepository
import io.writeopia.ui.model.LinkCardCreation
import io.writeopia.ui.model.TextInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private fun managerWith(
        vararg lines: String?,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
    ): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = dispatcher,
            userRepository = userRepository,
        )
        val content = buildMap {
            put(Rank.at(0), StoryStep(type = StoryTypes.TITLE.type, text = "Title"))
            lines.forEachIndexed { index, text ->
                put(Rank.at(index + 1), StoryStep(type = StoryTypes.TEXT.type, text = text))
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
            position = Rank.at(1),
            lineBreakByContent = true
        )
        advanceUntilIdle()

        val card = manager.currentStory.value.stories[Rank.at(1)]!!
        assertEquals(StoryTypes.LINK_CARD.type, card.type)
        assertEquals("https://writeopia.io/docs", card.url)
        assertNull(card.text, "the description comes from the backend later")

        assertEquals(1, creations.size)
        assertEquals(card.id, creations.single().storyStep.id)
        assertEquals(Rank.at(1), creations.single().position)

        // The card can not be typed in: an empty line was added below it and has the cursor.
        val state = manager.currentStory.value
        assertEquals(3, state.stories.size)
        val below = state.stories.entries.first { (position, _) -> position > Rank.at(1) }
        assertEquals(StoryTypes.TEXT.type, below.value.type)
        assertEquals("", below.value.text)
        assertEquals(below.key, state.focus)
        job.cancel()
    }

    @Test
    fun theCardSurvivesWorkScheduledOnTheDispatcher() = runTest {
        // A dispatcher that runs nothing until asked, like Dispatchers.Default in the app:
        // anything the creation leaves to a coroutine would land after the card and could undo it.
        val manager = managerWith(null, dispatcher = StandardTestDispatcher(testScheduler))

        manager.handleTextInput(
            TextInput(text = "https://www.google.com/", start = 23, end = 23),
            position = Rank.at(1),
            lineBreakByContent = true
        )
        val rightAfter = manager.currentStory.value
        assertEquals(StoryTypes.LINK_CARD.type, rightAfter.stories[Rank.at(1)]!!.type)
        assertEquals(3, rightAfter.stories.size)

        advanceUntilIdle()

        val settled = manager.currentStory.value
        assertEquals(StoryTypes.LINK_CARD.type, settled.stories[Rank.at(1)]!!.type, "the card was overwritten later")
        assertEquals("https://www.google.com/", settled.stories[Rank.at(1)]!!.url)
        assertEquals(3, settled.stories.size)
        val below = settled.stories.entries.first { (position, _) -> position > Rank.at(1) }
        assertEquals(StoryTypes.TEXT.type, below.value.type)
        assertEquals(below.key, settled.focus)
    }

    @Test
    fun pastingALinkAboveATextLineMovesTheCursorToThatLine() = runTest {
        val manager = managerWith(null, "below")

        manager.handleTextInput(
            TextInput(text = "https://writeopia.io", start = 20, end = 20),
            position = Rank.at(1),
            lineBreakByContent = true
        )
        advanceUntilIdle()

        val state = manager.currentStory.value
        assertEquals(StoryTypes.LINK_CARD.type, state.stories[Rank.at(1)]!!.type)
        assertEquals(3, state.stories.size, "no line is added when a text line is already below")
        assertEquals("below", state.stories[Rank.at(2)]!!.text)
        assertEquals(Rank.at(2), state.focus)
    }

    @Test
    fun pastingALinkInALineWithTextKeepsItAsText() = runTest {
        val manager = managerWith("See ")

        manager.handleTextInput(
            TextInput(text = "See https://writeopia.io", start = 24, end = 24),
            position = Rank.at(1),
            lineBreakByContent = true
        )
        advanceUntilIdle()

        val step = manager.currentStory.value.stories[Rank.at(1)]!!
        assertEquals(StoryTypes.TEXT.type, step.type)
        assertEquals("See https://writeopia.io", step.text)
    }

    @Test
    fun typingInAnEmptyLineKeepsItAsText() = runTest {
        val manager = managerWith(null)

        manager.handleTextInput(
            TextInput(text = "h", start = 1, end = 1),
            position = Rank.at(1),
            lineBreakByContent = true
        )
        advanceUntilIdle()

        val step = manager.currentStory.value.stories[Rank.at(1)]!!
        assertEquals(StoryTypes.TEXT.type, step.type)
        assertEquals("h", step.text)
    }

    @Test
    fun aServerStepIsAppliedWhereItsIdIsInTheDocument() = runTest {
        val manager = managerWith(null)
        manager.handleTextInput(
            TextInput(text = "https://writeopia.io", start = 20, end = 20),
            position = Rank.at(1),
            lineBreakByContent = true
        )
        advanceUntilIdle()
        val card = manager.currentStory.value.stories[Rank.at(1)]!!

        val applied = manager.applyServerStoryStep(card.copy(text = "A text editor for everyone."))
        advanceUntilIdle()

        assertTrue(applied)
        val described = manager.currentStory.value.stories[Rank.at(1)]!!
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
        assertEquals("hello", manager.currentStory.value.stories[Rank.at(1)]!!.text)
    }
}
