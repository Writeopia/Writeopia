package io.writeopia.claudecode

import io.writeopia.claudecode.markdown.LiveMarkdown
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class LiveWriterTest {

    @BeforeTest
    fun noJitter() {
        Rank.jitter = false
    }

    private fun manager(vararg steps: StoryStep): WriteopiaStateManager {
        val now = Clock.System.now()
        return WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        ).apply {
            loadDocument(
                Document(
                    content = steps.withIndex().associate { (index, step) -> Rank.at(index) to step },
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                )
            )
        }
    }

    private fun steps(manager: WriteopiaStateManager) =
        manager.currentStory.value.stories.values.filter { it.type != StoryTypes.SPACE.type }

    @Test
    fun `writes the title and the blocks after everything there`() = runTest {
        val manager = manager(
            StoryStep(id = "title", type = StoryTypes.TITLE.type, text = ""),
            StoryStep(id = "p1", type = StoryTypes.TEXT.type, text = "Already here."),
        )

        LiveWriter(tick = 1.milliseconds).write(
            manager,
            title = "Trip",
            blocks = LiveMarkdown.blocks("## Day one\nWe **left** early.\n---"),
        )

        val written = steps(manager)
        assertEquals(listOf("Trip", "Already here.", "Day one", "We left early.", null), written.map { it.text })
        assertEquals("title", written[0].id)
        assertTrue(written.none { it.ephemeral })
        assertEquals(StoryTypes.DIVIDER.type, written.last().type)
    }

    @Test
    fun `fills the empty paragraph at the end instead of leaving it, keeping its id`() = runTest {
        val manager = manager(
            StoryStep(id = "title", type = StoryTypes.TITLE.type, text = "Notes"),
            StoryStep(id = "empty", type = StoryTypes.TEXT.type, text = ""),
        )

        LiveWriter(tick = 1.milliseconds).write(manager, null, LiveMarkdown.blocks("First\nSecond"))

        val written = steps(manager)
        assertEquals(listOf("Notes", "First", "Second"), written.map { it.text })
        assertEquals("empty", written[1].id)
    }

    @Test
    fun `the text grows while it is typed and is ephemeral until whole`() = runTest {
        val manager = manager(StoryStep(id = "title", type = StoryTypes.TITLE.type, text = "Notes"))
        val seen = mutableListOf<StoryStep>()

        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.currentStory.collect { state ->
                state.stories.values.lastOrNull()?.takeIf { it.type == StoryTypes.TEXT.type }?.let(seen::add)
            }
        }

        LiveWriter(tick = 1.milliseconds, minCharsPerTick = 3).write(
            manager,
            null,
            LiveMarkdown.blocks("Hello **world**"),
        )
        runCurrent()
        collecting.cancel()

        val partials = seen.filter { it.ephemeral }.map { it.text }
        assertEquals(listOf("", "Hel", "Hello ", "Hello wor", "Hello world"), partials)
        assertEquals("Hello world", seen.last().text)
        assertTrue(!seen.last().ephemeral)
    }
}
