package io.writeopia.writingagent.actions

import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ai.AiCommand
import io.writeopia.ai.AiImageGenerator
import io.writeopia.ai.AiStreaming
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class WriteopiaActionExecutorTest {

    /** Exact keys: the document positions are deterministic in these tests. */
    @BeforeTest
    fun noJitter() {
        Rank.jitter = false
    }

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
                    content = mapOf(Rank.at(0) to title, Rank.at(1) to first, Rank.at(2) to second),
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
    fun `a code snippet written by the AI goes right below the paragraph, one code block per line`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(
            manager,
            resolveAi = { ai("Here it is:\n```kotlin\nfun main() {\n    println(\"hi\")\n}\n```\nDone.") }
        )

        executor.execute(WritingSuggestionAction.CODE_BLOCK, "p1", WritingAgentUi.None)

        assertEquals(
            listOf(
                StoryTypes.TITLE,
                StoryTypes.TEXT,
                StoryTypes.CODE_BLOCK,
                StoryTypes.CODE_BLOCK,
                StoryTypes.CODE_BLOCK,
                StoryTypes.TEXT,
            ),
            manager.typesInOrder()
        )
        assertEquals(
            listOf("Groceries", "We need milk.", "fun main() {", "    println(\"hi\")", "}", "And eggs."),
            manager.textsInOrder()
        )
    }

    @Test
    fun `without an AI the code block is empty, right below the paragraph`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null })

        executor.execute(WritingSuggestionAction.CODE_BLOCK, "p1", WritingAgentUi.None)

        assertEquals(
            listOf(StoryTypes.TITLE, StoryTypes.TEXT, StoryTypes.CODE_BLOCK, StoryTypes.TEXT),
            manager.typesInOrder()
        )
        // Right after the paragraph, half way to the next one
        assertEquals("", manager.currentStory.value.stories[Rank.between(Rank.at(1), Rank.at(2))]?.text)
    }

    @Test
    fun `a list is written from the paragraph, one block per item, right below it`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(
            manager,
            resolveAi = { ai("Here you go:\n- Milk\n- Eggs\n- Bread\n") }
        )

        executor.execute(WritingSuggestionAction.LIST, "p1", WritingAgentUi.None)

        assertEquals(
            listOf("Groceries", "We need milk.", "Milk", "Eggs", "Bread", "And eggs."),
            manager.textsInOrder()
        )
        assertEquals(
            listOf(StoryTypes.UNORDERED_LIST_ITEM, StoryTypes.UNORDERED_LIST_ITEM, StoryTypes.UNORDERED_LIST_ITEM),
            manager.typesInOrder().subList(2, 5)
        )
    }

    @Test
    fun `a checklist without an AI is a single empty item below the paragraph, found by its id after a move`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null })

        // Something was inserted above, so the paragraph is not where it was.
        manager.addAtPosition(StoryStep(type = StoryTypes.TEXT.type, text = "Intro."), Rank.at(1))
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

        executor.execute(WritingSuggestionAction.SECTION_HEADING, "p2", WritingAgentUi.None)

        assertEquals(listOf("Groceries", "We need milk.", "Shopping list", "And eggs."), manager.textsInOrder())
        // Right before the paragraph, which keeps its position
        val heading = manager.currentStory.value.stories[Rank.between(Rank.at(1), Rank.at(2))]
        assertNotNull(heading)
        assertTrue(heading.tags.any { it.tag == Tag.H2 })
    }

    @Test
    fun `the document title is written into the title block`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { ai("\"Weekly groceries.\"\n") })

        val localIdBefore = manager.currentStory.value.stories[Rank.at(0)]?.localId

        executor.execute(WritingSuggestionAction.DOCUMENT_TITLE, null, WritingAgentUi.None)

        val titleStep = manager.currentStory.value.stories[Rank.at(0)]
        assertNotNull(titleStep)
        assertEquals(StoryTypes.TITLE.type, titleStep.type)
        assertEquals("Weekly groceries", titleStep.text)
        assertEquals("title", titleStep.id)
        // A new localId is what makes the screen reload the text of the block.
        assertNotEquals(localIdBefore, titleStep.localId)
        assertEquals(3, manager.currentStory.value.stories.size)
    }

    @Test
    fun `a document without a title block gets one at the top`() = runTest {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        ).apply {
            loadDocument(
                Document(
                    content = mapOf(Rank.at(0) to first, Rank.at(1) to second),
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                )
            )
        }
        val executor = WriteopiaActionExecutor(manager, resolveAi = { ai("Groceries") })

        executor.execute(WritingSuggestionAction.DOCUMENT_TITLE, null, WritingAgentUi.None)

        assertEquals(listOf(StoryTypes.TITLE, StoryTypes.TEXT, StoryTypes.TEXT), manager.typesInOrder())
        // Before the first paragraph, which keeps its position
        assertEquals("Groceries", manager.textsInOrder().first())
        assertEquals("We need milk.", manager.currentStory.value.stories[Rank.at(0)]?.text)
    }

    @Test
    fun `without an AI the document title stays as it was`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null })

        executor.execute(WritingSuggestionAction.DOCUMENT_TITLE, null, WritingAgentUi.None)

        assertEquals("Groceries", manager.currentStory.value.stories[Rank.at(0)]?.text)
    }

    @Test
    fun `a TLDR goes under the title and a conclusion at the end`() = runTest {
        val manager = manager()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { ai("Short version.") })

        executor.execute(WritingSuggestionAction.TLDR, "title", WritingAgentUi.None)
        executor.execute(WritingSuggestionAction.CONCLUSION, "title", WritingAgentUi.None)

        assertEquals(
            listOf("Groceries", "TLDR: Short version.", "We need milk.", "And eggs.", "Short version."),
            manager.textsInOrder()
        )
        // The TL;DR is a card under the title; the conclusion is a plain AI answer.
        // Right after the title, half way to the first paragraph
        val tldr = manager.currentStory.value.stories[Rank.between(Rank.at(0), Rank.at(1))]
        assertNotNull(tldr)
        assertEquals(StoryTypes.TEXT.type, tldr.type)
        assertTrue(tldr.tags.any { it.tag == Tag.CARD_BLOCK })
        assertEquals(StoryTypes.AI_ANSWER, manager.typesInOrder().last())
    }

    @Test
    fun `a streamed answer is one step, rewritten at each chunk`() = runTest {
        val manager = manager()
        val edits = mutableListOf<StoryStep>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.currentStory.collect { state ->
                (state.lastEdit as? LastEdit.LineEdition)?.storyStep
                    ?.takeIf { step -> step.type == StoryTypes.AI_ANSWER.type }
                    ?.let(edits::add)
            }
        }
        val executor = WriteopiaActionExecutor(manager, resolveAi = { ai("Short version.") })

        executor.execute(WritingSuggestionAction.CONCLUSION, "title", WritingAgentUi.None)
        watching.cancel()

        // The fake AI streams two chunks: both went to the same step, so only one is ever saved,
        // and only once the stream ended: the chunks are ephemeral, the final text is not.
        val answer = manager.currentStory.value.stories.values.last()
        assertEquals(StoryTypes.AI_ANSWER.type, answer.type)
        assertEquals("Short version.", answer.text)
        assertEquals(setOf(answer.id), edits.map { step -> step.id }.toSet())
        assertEquals(listOf(true, true, false), edits.map { step -> step.ephemeral })
        assertTrue(!answer.ephemeral)
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

        // Right after the paragraph, half way to the next one
        val spreadsheet = manager.currentStory.value.stories[Rank.between(Rank.at(1), Rank.at(2))]
        assertNotNull(spreadsheet)
        assertEquals(StoryTypes.SPREADSHEET.type, spreadsheet.type)
        assertEquals(
            listOf(listOf("Item", "Qty"), listOf("Milk", "1"), listOf("Eggs", "12")),
            spreadsheet.steps.map { row -> row.steps.map { it.text } }
        )
    }

    @Test
    fun `an image drawn by the cloud goes right below the paragraph with its URL`() = runTest {
        val manager = manager()
        val requests = mutableListOf<Triple<String, String, String>>()
        var loadingShownWhileDrawing = false
        val generator = object : AiImageGenerator {
            override suspend fun generateImage(paragraph: String, documentTitle: String, documentText: String): ResultData<String> {
                requests.add(Triple(paragraph, documentTitle, documentText))
                loadingShownWhileDrawing = manager.typesInOrder() ==
                    listOf(StoryTypes.TITLE, StoryTypes.TEXT, StoryTypes.LOADING, StoryTypes.TEXT)
                return ResultData.Complete("https://images.test/milk.png")
            }
        }
        val picked = mutableListOf<String>()
        val ui = object : WritingAgentUi {
            override fun pickImage(onPicked: (String) -> Unit) {
                picked.add("opened")
            }

            override fun openDrawing() {}
        }
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null }, resolveImageAi = { generator })

        executor.execute(WritingSuggestionAction.IMAGE, "p1", ui)

        // The whole document went as context, not only the paragraph.
        assertEquals(1, requests.size)
        assertEquals("We need milk.", requests.single().first)
        assertEquals("Groceries", requests.single().second)
        assertTrue(requests.single().third.contains("And eggs."))

        assertTrue(loadingShownWhileDrawing, "a loading block should stand where the image will go")
        assertEquals(
            listOf(StoryTypes.TITLE, StoryTypes.TEXT, StoryTypes.IMAGE, StoryTypes.TEXT),
            manager.typesInOrder()
        )
        // Right after the paragraph, half way to the next one
        val image = manager.currentStory.value.stories[Rank.between(Rank.at(1), Rank.at(2))]
        assertNotNull(image)
        assertEquals("https://images.test/milk.png", image.url)
        assertTrue(picked.isEmpty(), "the picker should not open when the cloud draws")
    }

    @Test
    fun `when the cloud fails to draw the loading block goes away and nothing is inserted`() = runTest {
        val manager = manager()
        val generator = object : AiImageGenerator {
            override suspend fun generateImage(paragraph: String, documentTitle: String, documentText: String): ResultData<String> =
                ResultData.Error(Exception("quota"))
        }
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null }, resolveImageAi = { generator })

        executor.execute(WritingSuggestionAction.IMAGE, "p1", WritingAgentUi.None)

        assertEquals(listOf(StoryTypes.TITLE, StoryTypes.TEXT, StoryTypes.TEXT), manager.typesInOrder())
    }

    @Test
    fun `without the cloud an image is picked and added below the paragraph`() = runTest {
        val manager = manager()
        val added = mutableListOf<Pair<String, String>>()
        val executor = WriteopiaActionExecutor(manager, resolveAi = { null }, addImage = { path, pos -> added.add(path to pos) })
        val ui = object : WritingAgentUi {
            override fun pickImage(onPicked: (String) -> Unit) = onPicked("/tmp/cat.png")

            override fun openDrawing() {}
        }

        executor.execute(WritingSuggestionAction.IMAGE, "p1", ui)

        // The position of the paragraph after it: the picked image is added before it
        assertEquals(listOf("/tmp/cat.png" to Rank.at(2)), added)
    }
}
