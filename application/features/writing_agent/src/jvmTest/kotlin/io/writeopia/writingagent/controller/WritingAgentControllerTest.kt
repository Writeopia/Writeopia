package io.writeopia.writingagent.controller

import io.writeopia.app.dto.writingagent.FolderCandidate
import io.writeopia.app.dto.writingagent.FolderSuggestionDto
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import io.writeopia.app.dto.writingagent.FolderSuggestionsResponse
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
import io.writeopia.writingagent.folders.DocumentFolders
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

private class FakeApi(
    var answer: List<WritingSuggestionDto> = emptyList(),
    var folderAnswer: List<FolderSuggestionDto> = emptyList(),
) : WritingAgentApi {
    val requests = mutableListOf<WritingSuggestionsRequest>()
    val folderRequests = mutableListOf<FolderSuggestionsRequest>()

    override suspend fun suggestions(request: WritingSuggestionsRequest): ResultData<WritingSuggestionsResponse> {
        requests.add(request)
        return ResultData.Complete(WritingSuggestionsResponse(answer))
    }

    override suspend fun folderSuggestions(request: FolderSuggestionsRequest): ResultData<FolderSuggestionsResponse> {
        folderRequests.add(request)
        return ResultData.Complete(FolderSuggestionsResponse(folderAnswer))
    }
}

private class FakeFolders(
    var currentFolder: String = "root",
    val folders: List<FolderCandidate> = (1..12).map { FolderCandidate("f$it", "Folder $it") },
) : DocumentFolders {
    val movedTo = mutableListOf<String>()
    val created = mutableListOf<Pair<String, String>>()

    override suspend fun currentFolderId(): String = currentFolder

    override suspend fun candidates(limit: Int): List<FolderCandidate> = folders.take(limit)

    override suspend fun moveTo(folderId: String) {
        movedTo.add(folderId)
    }

    override suspend fun createFolderAndMove(documentTitle: String, documentText: String): Result<String> {
        created.add(documentTitle to documentText)
        return Result.success("New folder")
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
        folders: FakeFolders? = null,
    ) = WritingAgentController(
        scope = this,
        api = api,
        storyFlow = story,
        documentIdFlow = documentId,
        documentTitle = { "Groceries" },
        executor = executor,
        folders = folders,
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
    fun `an untitled document with one sentence is offered a title without asking the backend`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val untitled = title.copy(text = "")
        val body = paragraph.copy(text = "We need milk.")
        val story = MutableStateFlow(state(untitled, body))
        val controller = controller(api, story)
        advanceUntilIdle()

        assertEquals(listOf(WritingSuggestionAction.DOCUMENT_TITLE), controller.state.value.suggestions.map { it.action })
        assertTrue(api.requests.isEmpty())
        controller.stop()
    }

    @Test
    fun `an untitled document without a finished sentence is not offered a title yet`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title.copy(text = ""), paragraph.copy(text = "We need milk and eggs")))
        val controller = controller(api, story)
        advanceUntilIdle()

        assertTrue(controller.state.value.suggestions.isEmpty())
        controller.stop()
    }

    @Test
    fun `the title offer goes away once the user writes a title`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val body = paragraph.copy(text = "We need milk and eggs.")
        val story = MutableStateFlow(state(title.copy(text = ""), body))
        val controller = controller(api, story)
        advanceUntilIdle()
        assertTrue(controller.state.value.hasSuggestions)

        story.value = state(title.copy(text = "Groceries"), body)
        advanceUntilIdle()

        assertTrue(controller.state.value.suggestions.isEmpty())
        controller.stop()
    }

    @Test
    fun `applying the title offer runs it and does not offer again for this document`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val executor = FakeExecutor()
        val body = paragraph.copy(text = "We need milk and eggs.")
        val story = MutableStateFlow(state(title.copy(text = ""), body))
        val controller = controller(api, story, executor = executor)
        advanceUntilIdle()

        controller.execute(WritingSuggestionAction.DOCUMENT_TITLE, WritingAgentUi.None)
        advanceUntilIdle()
        story.value = state(title.copy(text = ""), body.copy(text = "We need milk and eggs. And bread."))
        advanceUntilIdle()

        assertEquals(listOf(WritingSuggestionAction.DOCUMENT_TITLE), executor.executed.map { it.first })
        assertTrue(controller.state.value.suggestions.none { it.action == WritingSuggestionAction.DOCUMENT_TITLE })
        controller.stop()
    }

    @Test
    fun `the title offer comes first, before what the backend answered`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(answer = listOf(WritingSuggestionDto(WritingSuggestionAction.LIST, 0.9)))
        val body = paragraph.copy(text = "We need milk and eggs")
        val story = MutableStateFlow(state(title.copy(text = ""), body))
        val controller = controller(api, story)
        advanceUntilIdle()

        story.value = state(title.copy(text = ""), body.copy(text = "We need milk and eggs."))
        advanceTimeBy(101)
        advanceUntilIdle()

        assertEquals(
            listOf(WritingSuggestionAction.DOCUMENT_TITLE, WritingSuggestionAction.LIST),
            controller.state.value.suggestions.map { it.action }
        )
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

    private val longParagraph = StoryStep(
        id = "p1",
        type = StoryTypes.TEXT.type,
        text = "We need milk, eggs, bread and butter for the whole week ahead of us."
    )

    @Test
    fun `a document opened in the root is offered the folders that fit and a new folder`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("f2", 0.95), FolderSuggestionDto("f1", 0.9)))
        val folders = FakeFolders()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = folders)
        advanceUntilIdle()

        // Only the first ten folders are asked about.
        assertEquals(1, api.folderRequests.size)
        assertEquals(10, api.folderRequests.single().folders.size)
        assertEquals("Groceries", api.folderRequests.single().documentTitle)

        val state = controller.state.value
        assertEquals(listOf("Folder 2", "Folder 1"), state.folderSuggestions.map { it.name })
        assertTrue(state.offerNewFolder)
        assertTrue(state.hasSuggestions)
        controller.stop()
    }

    @Test
    fun `a document that already sits in a folder is not offered to move`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("f2", 0.95)))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders(currentFolder = "f3"))
        advanceUntilIdle()

        assertTrue(api.folderRequests.isEmpty())
        assertTrue(controller.state.value.folderSuggestions.isEmpty())
        assertEquals(false, controller.state.value.offerNewFolder)
        controller.stop()
    }

    @Test
    fun `a root document in a workspace without folders is only offered a new folder`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders(folders = emptyList()))
        advanceUntilIdle()

        assertTrue(api.folderRequests.isEmpty())
        assertTrue(controller.state.value.folderSuggestions.isEmpty())
        assertTrue(controller.state.value.offerNewFolder)
        controller.stop()
    }

    @Test
    fun `moving to an offered folder files the document and withdraws the offers`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("f2", 0.95)))
        val folders = FakeFolders()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = folders)
        advanceUntilIdle()

        controller.moveToFolder("f2")
        advanceUntilIdle()

        assertEquals(listOf("f2"), folders.movedTo)
        assertTrue(controller.state.value.folderSuggestions.isEmpty())
        assertEquals(false, controller.state.value.offerNewFolder)
        controller.stop()
    }

    @Test
    fun `creating a folder hands the document to the AI and withdraws the offers`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val folders = FakeFolders()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = folders)
        advanceUntilIdle()

        controller.createFolder()
        advanceUntilIdle()

        assertEquals(1, folders.created.size)
        assertEquals("Groceries", folders.created.single().first)
        assertTrue(folders.created.single().second.contains("We need milk"))
        assertEquals(false, controller.state.value.offerNewFolder)
        controller.stop()
    }

    @Test
    fun `dismissing withdraws the folder offers for this document`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("f2", 0.95)))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders())
        advanceUntilIdle()

        controller.dismiss()

        assertEquals(false, controller.state.value.hasSuggestions)
        controller.stop()
    }
}
