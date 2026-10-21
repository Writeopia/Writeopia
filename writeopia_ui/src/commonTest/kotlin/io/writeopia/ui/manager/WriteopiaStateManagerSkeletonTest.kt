package io.writeopia.ui.manager

import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The skeleton that stands in for text the AI is rewriting. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class WriteopiaStateManagerSkeletonTest {

    private val userRepository: UserRepository = object : UserRepository {
        override suspend fun getUser(): WriteopiaUser = WriteopiaUser.disconnectedUser()

        override suspend fun getWorkspace(): Workspace = Workspace.disconnectedWorkspace()
    }

    private fun manager(vararg lines: String): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
            userRepository = userRepository,
        )
        val content = buildMap {
            put(0.0, StoryStep(type = StoryTypes.TITLE.type, text = "Title"))
            lines.forEachIndexed { index, line ->
                put((index + 1).toDouble(), StoryStep(type = StoryTypes.TEXT.type, text = line))
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
    fun itReplacesTheSelectedLinesWithOneSkeletonHoldingTheirText() = runTest {
        val manager = manager("one", "two", "three", "four")
        val deletedIds = listOf(2.0, 3.0).map { manager.getStory(it)!!.id }
        manager.onSectionSelected(2.0)
        assertTrue(manager.isOnSelection)

        val position = manager.replaceWithSkeleton(setOf(2.0, 3.0))

        assertEquals(2.0, position)
        val stories = manager.currentStory.value.stories
        val skeleton = stories[2.0]!!
        assertEquals(StoryTypes.SKELETON.type, skeleton.type)
        assertEquals("two\nthree", skeleton.text)
        assertTrue(skeleton.ephemeral)
        assertNull(stories[3.0])
        assertEquals("one", stories[1.0]?.text)
        assertEquals("four", stories[4.0]?.text)
        assertFalse(manager.isOnSelection)
        assertEquals(
            deletedIds,
            (manager.currentStory.value.lastEdit as LastEdit.BulkDeleteEdition).deletedIds
        )
    }

    @Test
    fun itReplacesNothingWithoutPositions() = runTest {
        val manager = manager("one")

        assertNull(manager.replaceWithSkeleton(emptySet()))
        assertNull(manager.replaceWithSkeleton(setOf(9.0)))
        assertEquals("one", manager.currentStory.value.stories[1.0]?.text)
    }

    @Test
    fun itPutsTheNewTextWhereTheSkeletonWasOneStoryPerLine() = runTest {
        val manager = manager("one", "two", "three", "four")
        val position = manager.replaceWithSkeleton(setOf(2.0, 3.0))!!

        manager.replaceSkeletonWithText(position, "new two\nnew three\nnew four")

        val texts = manager.currentStory.value.stories.entries
            .sortedBy { (position, _) -> position }
            .filter { (_, step) -> step.type == StoryTypes.TEXT.type }
            .map { (_, step) -> step.text }
        assertEquals(listOf("one", "new two", "new three", "new four", "four"), texts)
        assertTrue(manager.currentStory.value.stories.values.none { it.type == StoryTypes.SKELETON.type })
    }

    @Test
    fun itAppliesTheMarkdownMarksOfTheNewText() = runTest {
        val manager = manager("one", "two")
        val position = manager.replaceWithSkeleton(setOf(2.0))!!

        manager.replaceSkeletonWithText(position, "- first\n- second")

        val steps = manager.currentStory.value.stories.entries
            .sortedBy { (position, _) -> position }
            .map { (_, step) -> step }
            .filter { step -> step.type == StoryTypes.UNORDERED_LIST_ITEM.type }
        assertEquals(listOf("first", "second"), steps.map { it.text })
    }

    @Test
    fun itDoesNothingWhenTheSkeletonIsGone() = runTest {
        val manager = manager("one", "two")

        manager.replaceSkeletonWithText(2.0, "replacement")

        assertEquals("two", manager.currentStory.value.stories[2.0]?.text)
    }

    @Test
    fun aRegionRewriteDeletesTheRegionAndKeepsTheBlocksItIsGiven() = runTest {
        val manager = manager("one", "two", "three")
        val stories = manager.currentStory.value.stories
        val keptBlock = StoryStep(type = StoryTypes.IMAGE.type, url = "pic")
        val deletedIds = listOf(2.0, 3.0).map { stories[it]!!.id }
        val skeleton = StoryStep(type = StoryTypes.SKELETON.type, text = "two\nthree", ephemeral = true)

        val begun = manager.beginRegionRewrite(from = 2.0, toExclusive = null, stories = listOf(skeleton, keptBlock))

        assertTrue(begun)
        val after = manager.currentStory.value
        assertEquals("one", after.stories[1.0]?.text)
        assertEquals(StoryTypes.SKELETON.type, after.stories[2.0]?.type)
        assertEquals(keptBlock.id, after.stories[3.0]?.id)
        assertEquals(4, after.stories.size)
        assertEquals(deletedIds, (after.lastEdit as LastEdit.BulkDeleteEdition).deletedIds)
    }

    @Test
    fun aRegionRewriteOfNothingDoesNothing() = runTest {
        val manager = manager("one")

        assertFalse(manager.beginRegionRewrite(from = 5.0, toExclusive = null, stories = emptyList()))
        assertEquals("one", manager.currentStory.value.stories[1.0]?.text)
    }

    @Test
    fun theRegionUpdatesStayInsideTheRegion() = runTest {
        val manager = manager("one", "two", "three", "four")
        manager.beginRegionRewrite(from = 2.0, toExclusive = 4.0, stories = emptyList())

        val rewritten = listOf("a", "b", "c").map { StoryStep(type = StoryTypes.TEXT.type, text = it) }
        manager.updateRegion(from = 2.0, toExclusive = 4.0, stories = rewritten, done = false)

        val texts = manager.currentStory.value.stories.entries
            .sortedBy { (position, _) -> position }
            .map { (_, step) -> step.text }
        assertEquals(listOf("Title", "one", "a", "b", "c", "four"), texts)
        assertTrue(manager.currentStory.value.stories.keys.filter { it >= 2.0 && it < 4.0 }.size == 3)
        assertEquals(LastEdit.Nothing, manager.currentStory.value.lastEdit)
    }

    @Test
    fun theFinishedRegionIsSavedWholeWithItsLinks() = runTest {
        val manager = manager("one", "two")
        manager.beginRegionRewrite(from = 2.0, toExclusive = null, stories = emptyList())

        val rewritten = listOf("a", "b").map { StoryStep(type = StoryTypes.TEXT.type, text = it) }
        manager.updateRegion(from = 2.0, toExclusive = null, stories = rewritten, done = true)

        val state = manager.currentStory.value
        assertEquals(LastEdit.Whole, state.lastEdit)
        assertEquals(3.0, state.stories[2.0]?.nextPosition)
        assertEquals(2.0, state.stories[3.0]?.previousPosition)
        assertNull(state.stories[3.0]?.nextPosition)
    }

    @Test
    fun aRegionRewriteCanBeUndone() = runTest {
        val manager = manager("one", "two", "three")
        manager.beginRegionRewrite(from = 1.0, toExclusive = null, stories = emptyList())
        manager.updateRegion(
            from = 1.0,
            toExclusive = null,
            stories = listOf(StoryStep(type = StoryTypes.TEXT.type, text = "rewritten")),
            done = true
        )

        manager.undo()

        val texts = manager.currentStory.value.stories.values
            .filter { step -> step.type == StoryTypes.TEXT.type }
            .map { step -> step.text }
        assertEquals(listOf("one", "two", "three"), texts)
    }

    @Test
    fun theReplacementCanBeUndone() = runTest {
        val manager = manager("one", "two", "three")
        val position = manager.replaceWithSkeleton(setOf(2.0, 3.0))!!
        manager.replaceSkeletonWithText(position, "rewritten")

        manager.undo()

        val texts = manager.currentStory.value.stories.values
            .filter { step -> step.type == StoryTypes.TEXT.type }
            .map { step -> step.text }
        assertEquals(listOf("one", "two", "three"), texts)
    }
}
