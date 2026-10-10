package io.writeopia.writingagent.controller

import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionDto
import io.writeopia.app.dto.writingagent.WritingSuggestionScope
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import io.writeopia.app.dto.writingagent.WritingSuggestionsResponse
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.writingagent.actions.WritingActionExecutor
import io.writeopia.writingagent.actions.WritingAgentUi
import io.writeopia.writingagent.api.WritingAgentApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeApi(var answer: List<WritingSuggestionDto> = emptyList()) : WritingAgentApi {
    val requests = mutableListOf<WritingSuggestionsRequest>()

    override suspend fun suggestions(request: WritingSuggestionsRequest): ResultData<WritingSuggestionsResponse> {
        requests.add(request)
        return ResultData.Complete(WritingSuggestionsResponse(answer))
    }
}

private class FakeExecutor : WritingActionExecutor {
    val executed = mutableListOf<Pair<WritingSuggestionAction, String?>>()

    override suspend fun execute(action: WritingSuggestionAction, storyStepId: String?, ui: WritingAgentUi) {
        executed.add(action to storyStepId)
    }
}

class WritingAgentControllerTest {

    private val title = StoryStep(id = "title", type = StoryTypes.TITLE.type, text = "Groceries")
    private val paragraph = StoryStep(id = "p1", type = StoryTypes.TEXT.type, text = "We need milk")

    private fun state(vararg steps: StoryStep, focus: Double? = 1.0) =
        StoryState(
            stories = steps.withIndex().associate { (i, step) -> i.toDouble() to step },
            focus = focus
        )

    private fun TestScope.controller(
        api: FakeApi,
        story: MutableStateFlow<StoryState>,
        documentId: MutableStateFlow<String> = MutableStateFlow("doc"),
        executor: FakeExecutor = FakeExecutor(),
        enabled: Boolean = true,
    ) = WritingAgentController(
        scope = this,
        api = api,
        storyFlow = story,
        documentIdFlow = documentId,
        documentTitle = { "Groceries" },
        executor = executor,
        isEnabled = { enabled },
        debounceMillis = 100,
        minimumDocumentLength = 40,
    ).also { it.start() }

    @Test
    fun `a new sentence asks for suggestions about the focused paragraph`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(answer = listOf(WritingSuggestionDto(WritingSuggestionAction.CHECK_LIST, 0.9)))
        val story = MutableStateFlow(state(title, paragraph))
        val controller = controller(api, story)
        advanceUntilIdle()
        assertTrue(api.requests.isEmpty(), "the first sight of a paragraph only records it")

        story.value = state(title, paragraph.copy(text = "We need milk."))
        advanceTimeBy(101)
        advanceUntilIdle()

        val request = api.requests.single()
        assertEquals(WritingSuggestionScope.WRITING, request.scope)
        assertEquals("We need milk.", request.text)
        assertEquals("p1", controller.state.value.storyStepId)
        assertEquals(listOf(WritingSuggestionAction.CHECK_LIST), controller.state.value.suggestions.map { it.action })

        controller.stop()
    }

    @Test
    fun `typing without ending a sentence asks nothing`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, paragraph))
        val controller = controller(api, story)
        advanceUntilIdle()

        story.value = state(title, paragraph.copy(text = "We need milk and"))
        story.value = state(title, paragraph.copy(text = "We need milk and eggs"))
        advanceTimeBy(500)
        advanceUntilIdle()

        assertTrue(api.requests.isEmpty())
        controller.stop()
    }

    @Test
    fun `quick successive sentences are asked once, after the pause`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, paragraph))
        val controller = controller(api, story)
        advanceUntilIdle()

        story.value = state(title, paragraph.copy(text = "We need milk."))
        advanceTimeBy(50)
        story.value = state(title, paragraph.copy(text = "We need milk. And eggs."))
        advanceTimeBy(101)
        advanceUntilIdle()

        assertEquals(1, api.requests.size)
        assertEquals("We need milk. And eggs.", api.requests.single().text)
        controller.stop()
    }

    @Test
    fun `nothing is shown when nothing comes back`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(answer = emptyList())
        val story = MutableStateFlow(state(title, paragraph))
        val controller = controller(api, story)
        advanceUntilIdle()

        story.value = state(title, paragraph.copy(text = "We need milk."))
        advanceTimeBy(101)
        advanceUntilIdle()

        assertEquals(1, api.requests.size)
        assertTrue(controller.state.value.suggestions.isEmpty())
        controller.stop()
    }

    @Test
    fun `an opened document is asked about once, anchored to its title`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(answer = listOf(WritingSuggestionDto(WritingSuggestionAction.TLDR, 0.95)))
        val longParagraph = paragraph.copy(text = "A".repeat(50))
        val story = MutableStateFlow(state(title, longParagraph, focus = null))
        val documentId = MutableStateFlow("doc")
        val controller = controller(api, story, documentId)
        advanceUntilIdle()

        story.value = state(title, longParagraph.copy(text = "A".repeat(60)), focus = null)
        advanceUntilIdle()

        val request = api.requests.single()
        assertEquals(WritingSuggestionScope.DOCUMENT_OPENED, request.scope)
        assertEquals("title", controller.state.value.storyStepId)
        assertEquals(WritingSuggestionScope.DOCUMENT_OPENED, controller.state.value.scope)
        controller.stop()
    }

    @Test
    fun `a short opened document is not asked about`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, paragraph, focus = null))
        val controller = controller(api, story)
        advanceUntilIdle()

        assertTrue(api.requests.isEmpty())
        controller.stop()
    }

    @Test
    fun `nothing is asked when the agent is disabled`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, paragraph))
        val controller = controller(api, story, enabled = false)
        advanceUntilIdle()

        story.value = state(title, paragraph.copy(text = "We need milk."))
        advanceTimeBy(101)
        advanceUntilIdle()

        assertTrue(api.requests.isEmpty())
        controller.stop()
    }

    @Test
    fun `applying a suggestion runs it on the remembered paragraph and drops it`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(
            answer = listOf(
                WritingSuggestionDto(WritingSuggestionAction.CHECK_LIST, 0.9),
                WritingSuggestionDto(WritingSuggestionAction.LIST, 0.85),
            )
        )
        val executor = FakeExecutor()
        val story = MutableStateFlow(state(title, paragraph))
        val controller = controller(api, story, executor = executor)
        advanceUntilIdle()

        story.value = state(title, paragraph.copy(text = "We need milk."))
        advanceTimeBy(101)
        advanceUntilIdle()

        controller.execute(WritingSuggestionAction.CHECK_LIST, WritingAgentUi.None)
        advanceUntilIdle()

        assertEquals(listOf<Pair<WritingSuggestionAction, String?>>(WritingSuggestionAction.CHECK_LIST to "p1"), executor.executed)
        assertEquals(listOf(WritingSuggestionAction.LIST), controller.state.value.suggestions.map { it.action })
        controller.stop()
    }

    @Test
    fun `dismissing hides the suggestions`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(answer = listOf(WritingSuggestionDto(WritingSuggestionAction.LIST, 0.9)))
        val story = MutableStateFlow(state(title, paragraph))
        val controller = controller(api, story)
        advanceUntilIdle()

        story.value = state(title, paragraph.copy(text = "We need milk."))
        advanceTimeBy(101)
        advanceUntilIdle()
        assertTrue(controller.state.value.hasSuggestions)

        controller.dismiss()

        assertTrue(controller.state.value.suggestions.isEmpty())
        assertNull(controller.state.value.storyStepId)
        controller.stop()
    }
}
