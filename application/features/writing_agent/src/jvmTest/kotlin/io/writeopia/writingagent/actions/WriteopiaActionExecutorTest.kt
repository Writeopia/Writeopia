package io.writeopia.writingagent.actions

import io.writeopia.ai.AiCommand
import io.writeopia.ai.AiStreaming
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class WriteopiaActionExecutorTest {

    private val title = StoryStep(id = "title", type = StoryTypes.TITLE.type, text = "Groceries")
    private val first = StoryStep(id = "p1", type = StoryTypes.TEXT.type, text = "We need milk.")
    private val second = StoryStep(id = "p2", type = StoryTypes.TEXT.type, text = "And eggs.")

    private fun manager(): WriteopiaStateManager {
        val now = Clock.System.now()
        return WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        ).apply {
            loadDocument(
                Document(
                    content = mapOf(0.0 to title, 1.0 to first, 2.0 to second),
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                )
            )
        }
    }

    private fun ai(answer: String) = object : AiStreaming {
        override fun stream(command: AiCommand, prompt: String): Flow<ResultData<String>> =
            flowOf(ResultData.Complete(answer.take(3)), ResultData.Complete(answer))
    }

    private fun WriteopiaStateManager.typesInOrder() =
        currentStory.value.stories.toSortedMap().values.map { StoryTypes.fromNumber(it.type.number) }

    private fun WriteopiaStateManager.textsInOrder() =
        currentStory.value.stories.toSortedMap().values.map { it.text }

    @Test
    fun `a code block goes right below the paragraph`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null })

        executor.execute(WritingSuggestionAction.CODE_BLOCK, "p1", WritingAgentUi.None)

        assertEquals(
            listOf(StoryTypes.TITLE, StoryTypes.TEXT, StoryTypes.CODE_BLOCK, StoryTypes.TEXT),
            manager.typesInOrder()
        )
    }

    @Test
    fun `a checklist goes right below the paragraph, found by its id after a move`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null })

        // Something was inserted above, so the paragraph is not where it was.
        manager.addAtPosition(StoryStep(type = StoryTypes.TEXT.type, text = "Intro."), 1.0)
        executor.execute(WritingSuggestionAction.CHECK_LIST, "p1", WritingAgentUi.None)

        assertEquals(
            listOf(StoryTypes.TITLE, StoryTypes.TEXT, StoryTypes.TEXT, StoryTypes.CHECK_ITEM, StoryTypes.TEXT),
            manager.typesInOrder()
        )
    }

    @Test
    fun `a callout highlights the paragraph`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null })

        executor.execute(WritingSuggestionAction.CALLOUT, "p2", WritingAgentUi.None)

        val step = manager.currentStory.value.stories.values.first { it.id == "p2" }
        assertTrue(step.tags.any { it.tag == Tag.HIGH_LIGHT_BLOCK })
    }

    @Test
    fun `a heading goes above the paragraph with the text the AI wrote`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { ai("## Shopping list\n") })

        executor.execute(WritingSuggestionAction.TITLE, "p2", WritingAgentUi.None)

        assertEquals(listOf("Groceries", "We need milk.", "Shopping list", "And eggs."), manager.textsInOrder())
        val heading = manager.currentStory.value.stories[2.0]
        assertNotNull(heading)
        assertTrue(heading.tags.any { it.tag == Tag.H2 })
    }

    @Test
    fun `a TLDR goes under the title and a conclusion at the end`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { ai("Short version.") })

        executor.execute(WritingSuggestionAction.TLDR, "title", WritingAgentUi.None)
        executor.execute(WritingSuggestionAction.CONCLUSION, "title", WritingAgentUi.None)

        assertEquals(
            listOf("Groceries", "Short version.", "We need milk.", "And eggs.", "Short version."),
            manager.textsInOrder()
        )
        assertEquals(StoryTypes.AI_ANSWER, manager.typesInOrder()[1])
    }

    @Test
    fun `without an AI the text actions say so in the document`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null })

        executor.execute(WritingSuggestionAction.CONCLUSION, "title", WritingAgentUi.None)

        assertEquals(AiTextWriter.NO_AI_MESSAGE, manager.textsInOrder().last())
    }

    @Test
    fun `a spreadsheet below the paragraph is filled with the table the AI wrote`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(
            manager,
            resolveAi = { ai("| Item | Qty |\n|---|---|\n| Milk | 1 |\n| Eggs | 12 |") }
        )

        executor.execute(WritingSuggestionAction.SPREADSHEET, "p1", WritingAgentUi.None)

        val spreadsheet = manager.currentStory.value.stories[2.0]
        assertNotNull(spreadsheet)
        assertEquals(StoryTypes.SPREADSHEET.type, spreadsheet.type)
        assertEquals(
            listOf(listOf("Item", "Qty"), listOf("Milk", "1"), listOf("Eggs", "12")),
            spreadsheet.steps.map { row -> row.steps.map { it.text } }
        )
    }

    @Test
    fun `an image is picked and added below the paragraph`() = runTest {
        val manager = manager()
        val added = mutableListOf<Pair<String, Double>>()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null }, addImage = { path, pos -> added.add(path to pos) })
        val ui = object : WritingAgentUi {
            override fun pickImage(onPicked: (String) -> Unit) = onPicked("/tmp/cat.png")

            override fun openDrawing() {}
        }

        executor.execute(WritingSuggestionAction.IMAGE, "p1", ui)

        assertEquals(listOf("/tmp/cat.png" to 2.0), added)
    }
}
