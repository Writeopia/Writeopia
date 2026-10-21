package io.writeopia.writingagent.controller

import io.writeopia.app.dto.writingagent.FolderCandidate
import io.writeopia.app.dto.writingagent.FolderSuggestionDto
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import io.writeopia.app.dto.writingagent.FolderSuggestionsResponse
import io.writeopia.app.dto.writingagent.TagCandidate
import io.writeopia.app.dto.writingagent.TagCreateRequest
import io.writeopia.app.dto.writingagent.TagCreateResponse
import io.writeopia.app.dto.writingagent.TagSuggestionDto
import io.writeopia.app.dto.writingagent.TagSuggestionsRequest
import io.writeopia.app.dto.writingagent.TagSuggestionsResponse
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.writingagent.tags.DocumentTags
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeApi(
    var answer: List<WritingSuggestionDto> = emptyList(),
    var folderAnswer: List<FolderSuggestionDto> = emptyList(),
    var folderFailure: Exception? = null,
    var folderDelayMillis: Long = 0,
    var tagAnswer: List<TagSuggestionDto> = emptyList(),
    var tagFailure: Exception? = null,
    var tagDelayMillis: Long = 0,
) : WritingAgentApi {
    val requests = mutableListOf<WritingSuggestionsRequest>()
    val folderRequests = mutableListOf<FolderSuggestionsRequest>()
    val tagRequests = mutableListOf<TagSuggestionsRequest>()
    val createTagRequests = mutableListOf<TagCreateRequest>()

    override suspend fun tagSuggestions(request: TagSuggestionsRequest): ResultData<TagSuggestionsResponse> {
        tagRequests.add(request)
        if (tagDelayMillis > 0) delay(tagDelayMillis)
        tagFailure?.let { return ResultData.Error(it) }
        return ResultData.Complete(TagSuggestionsResponse(tagAnswer))
    }

    override suspend fun createTag(request: TagCreateRequest): ResultData<TagCreateResponse> {
        createTagRequests.add(request)
        return ResultData.Complete(TagCreateResponse(name = "Fresh", description = "Made by the AI"))
    }

    override suspend fun suggestions(request: WritingSuggestionsRequest): ResultData<WritingSuggestionsResponse> {
        requests.add(request)
        return ResultData.Complete(WritingSuggestionsResponse(answer))
    }

    override suspend fun folderSuggestions(request: FolderSuggestionsRequest): ResultData<FolderSuggestionsResponse> {
        folderRequests.add(request)
        if (folderDelayMillis > 0) delay(folderDelayMillis)
        folderFailure?.let { return ResultData.Error(it) }
        return ResultData.Complete(FolderSuggestionsResponse(folderAnswer))
    }
}

private class FakeFolders(
    var currentFolder: String = "root",
    val folders: List<FolderCandidate> = (1..12).map { FolderCandidate("f$it", "Folder $it") },
    /** Blank keeps the old behavior: the app's candidates are all the backend gets. */
    val workspace: String = "",
) : DocumentFolders {
    val movedTo = mutableListOf<String>()
    val created = mutableListOf<Pair<String, String>>()

    override suspend fun currentFolderId(): String = currentFolder

    override suspend fun workspaceId(): String = workspace

    override suspend fun candidates(limit: Int): List<FolderCandidate> = folders.take(limit)

    override suspend fun moveTo(folderId: String) {
        movedTo.add(folderId)
    }

    override suspend fun createFolderAndMove(documentTitle: String, documentText: String): Result<String> {
        created.add(documentTitle to documentText)
        return Result.success("New folder")
    }
}

private class FakeTags(
    var current: Set<String> = emptySet(),
    val catalogue: List<TagCandidate> = (1..35).map { TagCandidate("t$it", "Tag $it", "About $it") },
) : DocumentTags {
    val added = mutableListOf<Set<String>>()
    val created = mutableListOf<Pair<String, String>>()

    override fun currentTagIds(): Set<String> = current

    override suspend fun candidates(limit: Int): List<TagCandidate> = catalogue.take(limit)

    override suspend fun addTags(tagIds: Set<String>) {
        added.add(tagIds)
        current = current + tagIds
    }

    override suspend fun createTagWithAi(documentTitle: String, documentText: String): Result<DocumentTag> {
        created.add(documentTitle to documentText)
        current = current + "fresh"
        return Result.success(DocumentTag.create("Fresh", "Made by the AI", "workspace"))
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

    private fun state(vararg steps: StoryStep, focus: Long? = 1L) =
        StoryState(
            stories = steps.withIndex().associate { (i, step) -> i.toLong() to step },
            focus = focus
        )

    private fun TestScope.controller(
        api: FakeApi,
        story: MutableStateFlow<StoryState>,
        documentId: MutableStateFlow<String> = MutableStateFlow("doc"),
        executor: FakeExecutor = FakeExecutor(),
        enabled: Boolean = true,
        folders: FakeFolders? = null,
        tags: FakeTags? = null,
        startCollapsed: Boolean = false,
    ) = WritingAgentController(
        scope = this,
        api = api,
        storyFlow = story,
        documentIdFlow = documentId,
        documentTitle = { "Groceries" },
        executor = executor,
        folders = folders,
        tags = tags,
        isEnabled = { enabled },
        debounceMillis = 100,
        minimumDocumentLength = 40,
        folderMinimumDocumentLength = 30,
        tagMinimumDocumentLength = 30,
        startCollapsed = startCollapsed,
    ).also { it.start() }

    @Test
    fun `the box can start folded, as on phones, and still unfolds`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(answer = listOf(WritingSuggestionDto(WritingSuggestionAction.LIST, 0.9)))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, startCollapsed = true)
        advanceUntilIdle()

        assertTrue(controller.collapsed.value)
        controller.toggleCollapsed()
        assertEquals(false, controller.collapsed.value)
        controller.stop()
    }

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
    fun `a document without tags is offered some in the box, without asking the backend`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(tagAnswer = listOf(TagSuggestionDto("t2", 0.95)))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, tags = FakeTags())
        advanceUntilIdle()

        assertTrue(api.tagRequests.isEmpty())
        assertTrue(controller.state.value.offerTags)
        assertTrue(controller.state.value.hasSuggestions)
        assertEquals(1, controller.state.value.count)
        assertNull(controller.state.value.tagDialog)
        controller.stop()
    }

    @Test
    fun `a document that already has tags is not offered any, and an offer is withdrawn once it gets one`() = runTest(
        StandardTestDispatcher()
    ) {
        val tagged = FakeTags(current = setOf("t1"))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(FakeApi(), story, tags = tagged)
        advanceUntilIdle()
        assertEquals(false, controller.state.value.offerTags)
        controller.stop()

        val untagged = FakeTags()
        val story2 = MutableStateFlow(state(title, longParagraph))
        val controller2 = controller(FakeApi(), story2, tags = untagged)
        advanceUntilIdle()
        assertTrue(controller2.state.value.offerTags)

        untagged.current = setOf("t9")
        story2.value = state(title, longParagraph.copy(text = longParagraph.text + " More."))
        advanceUntilIdle()

        assertEquals(false, controller2.state.value.offerTags)
        controller2.stop()
    }

    @Test
    fun `a short document is offered tags only once it has enough text`() = runTest(StandardTestDispatcher()) {
        val story = MutableStateFlow(state(title, paragraph))
        val controller = controller(FakeApi(), story, tags = FakeTags())
        advanceUntilIdle()
        assertEquals(false, controller.state.value.offerTags)

        story.value = state(title, longParagraph)
        advanceUntilIdle()

        assertTrue(controller.state.value.offerTags)
        controller.stop()
    }

    @Test
    fun `taking the tag offer opens a dialog that spins, asks about thirty tags and lists the fits selected`() = runTest(
        StandardTestDispatcher()
    ) {
        val api = FakeApi(
            tagAnswer = listOf(TagSuggestionDto("t2", 0.95), TagSuggestionDto("t1", 0.9)),
            tagDelayMillis = 50,
        )
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, tags = FakeTags())
        advanceUntilIdle()

        controller.openTagDialog()
        assertEquals(true, controller.state.value.tagDialog?.isLoading)
        advanceUntilIdle()

        assertEquals(1, api.tagRequests.size)
        assertEquals(TagSuggestionsRequest.MAX_TAGS, api.tagRequests.single().tags.size)
        assertEquals("About 1", api.tagRequests.single().tags.first().description)
        assertEquals("Groceries", api.tagRequests.single().documentTitle)

        val dialog = controller.state.value.tagDialog
        assertNotNull(dialog)
        assertEquals(false, dialog.isLoading)
        assertEquals(listOf("Tag 2", "Tag 1"), dialog.options.map { it.name })
        assertEquals(setOf("t2", "t1"), dialog.selected)
        controller.stop()
    }

    @Test
    fun `in a workspace without tags the dialog only offers a new tag`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, tags = FakeTags(catalogue = emptyList()))
        advanceUntilIdle()

        controller.openTagDialog()
        advanceUntilIdle()

        assertTrue(api.tagRequests.isEmpty())
        val dialog = controller.state.value.tagDialog
        assertNotNull(dialog)
        assertEquals(false, dialog.isLoading)
        assertTrue(dialog.options.isEmpty())
        assertTrue(dialog.catalogueIsEmpty)
        controller.stop()
    }

    @Test
    fun `applying the dialog adds the tags still selected, closes it and withdraws the offer`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(tagAnswer = listOf(TagSuggestionDto("t2", 0.95), TagSuggestionDto("t1", 0.9)))
        val tags = FakeTags()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, tags = tags)
        advanceUntilIdle()
        controller.openTagDialog()
        advanceUntilIdle()

        controller.toggleTag("t1")
        assertEquals(setOf("t2"), controller.state.value.tagDialog?.selected)
        controller.applyTags()
        advanceUntilIdle()

        assertEquals(listOf(setOf("t2")), tags.added)
        assertNull(controller.state.value.tagDialog)
        assertEquals(false, controller.state.value.offerTags)
        controller.stop()
    }

    @Test
    fun `creating a tag spins in the dialog, uses the AI and withdraws the offer`() = runTest(StandardTestDispatcher()) {
        val tags = FakeTags()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(FakeApi(), story, tags = tags)
        advanceUntilIdle()
        controller.openTagDialog()
        advanceUntilIdle()

        controller.createTagWithAi()
        assertEquals(true, controller.state.value.tagDialog?.isCreating)
        advanceUntilIdle()

        assertEquals(1, tags.created.size)
        assertEquals("Groceries", tags.created.single().first)
        assertTrue(tags.created.single().second.contains("We need milk"))
        assertNull(controller.state.value.tagDialog)
        assertEquals(false, controller.state.value.offerTags)
        controller.stop()
    }

    @Test
    fun `dismissing the box withdraws the tag offer for this document`() = runTest(StandardTestDispatcher()) {
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(FakeApi(), story, tags = FakeTags())
        advanceUntilIdle()
        assertTrue(controller.state.value.offerTags)

        controller.dismiss()
        story.value = state(title, longParagraph.copy(text = longParagraph.text + " Again."))
        advanceUntilIdle()

        assertEquals(false, controller.state.value.offerTags)
        controller.stop()
    }

    @Test
    fun `a document opened in the root is offered a folder in the box, without asking the backend`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("f2", 0.95)))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders())
        advanceUntilIdle()

        assertTrue(api.folderRequests.isEmpty())
        assertTrue(controller.state.value.offerFolder)
        assertTrue(controller.state.value.hasSuggestions)
        assertNull(controller.state.value.folderDialog)
        controller.stop()
    }

    @Test
    fun `a document that already sits in a folder is not offered one`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("f2", 0.95)))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders(currentFolder = "f3"))
        advanceUntilIdle()

        assertTrue(api.folderRequests.isEmpty())
        assertEquals(false, controller.state.value.offerFolder)
        controller.stop()
    }

    @Test
    fun `taking the offer opens a dialog that spins, asks about ten folders and lists the fits`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(
            folderAnswer = listOf(FolderSuggestionDto("f2", 0.95), FolderSuggestionDto("f1", 0.9)),
            folderDelayMillis = 50,
        )
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders())
        advanceUntilIdle()

        controller.openFolderDialog()
        assertEquals(true, controller.state.value.folderDialog?.isLoading)
        advanceUntilIdle()

        assertEquals(1, api.folderRequests.size)
        assertEquals(10, api.folderRequests.single().folders.size)
        assertEquals("Groceries", api.folderRequests.single().documentTitle)

        val dialog = controller.state.value.folderDialog
        assertNotNull(dialog)
        assertEquals(false, dialog.isLoading)
        assertEquals(listOf("Folder 2", "Folder 1"), dialog.options.map { it.name })
        controller.stop()
    }

    @Test
    fun `in a workspace without folders the dialog only offers a new folder`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders(folders = emptyList()))
        advanceUntilIdle()

        controller.openFolderDialog()
        advanceUntilIdle()

        assertTrue(api.folderRequests.isEmpty())
        val dialog = controller.state.value.folderDialog
        assertNotNull(dialog)
        assertEquals(false, dialog.isLoading)
        assertTrue(dialog.options.isEmpty())
        controller.stop()
    }

    @Test
    fun `with a workspace the backend may answer a folder the app did not send, by its name`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(
            folderAnswer = listOf(
                FolderSuggestionDto("deep", 0.95, folderName = "Nested"),
                FolderSuggestionDto("f1", 0.9),
                FolderSuggestionDto("nameless", 0.85),
            ),
        )
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders(workspace = "ws-1"))
        advanceUntilIdle()

        controller.openFolderDialog()
        advanceUntilIdle()

        assertEquals("ws-1", api.folderRequests.single().workspaceId)
        assertEquals(10, api.folderRequests.single().folders.size)
        assertEquals(listOf("Nested", "Folder 1"), controller.state.value.folderDialog?.options?.map { it.name })
        controller.stop()
    }

    @Test
    fun `with a workspace the backend is asked even when the app has no folder to offer`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("deep", 0.95, folderName = "Nested")))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders(folders = emptyList(), workspace = "ws-1"))
        advanceUntilIdle()

        controller.openFolderDialog()
        advanceUntilIdle()

        assertEquals(1, api.folderRequests.size)
        assertEquals(listOf("Nested"), controller.state.value.folderDialog?.options?.map { it.name })
        controller.stop()
    }

    @Test
    fun `picking a folder in the dialog files the document, closes it and withdraws the offer`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("f2", 0.95)))
        val folders = FakeFolders()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = folders)
        advanceUntilIdle()
        controller.openFolderDialog()
        advanceUntilIdle()

        controller.moveToFolder("f2")
        advanceUntilIdle()

        assertEquals(listOf("f2"), folders.movedTo)
        assertNull(controller.state.value.folderDialog)
        assertEquals(false, controller.state.value.offerFolder)
        controller.stop()
    }

    @Test
    fun `creating a folder spins in the dialog, uses the AI and withdraws the offer`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val folders = FakeFolders()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = folders)
        advanceUntilIdle()
        controller.openFolderDialog()
        advanceUntilIdle()

        controller.createFolder()
        assertEquals(true, controller.state.value.folderDialog?.isCreating)
        advanceUntilIdle()

        assertEquals(1, folders.created.size)
        assertEquals("Groceries", folders.created.single().first)
        assertTrue(folders.created.single().second.contains("We need milk"))
        assertNull(controller.state.value.folderDialog)
        assertEquals(false, controller.state.value.offerFolder)
        controller.stop()
    }

    @Test
    fun `closing the dialog with not now keeps the offer in the box`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderAnswer = listOf(FolderSuggestionDto("f2", 0.95)))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders())
        advanceUntilIdle()
        controller.openFolderDialog()
        advanceUntilIdle()

        controller.dismissFolderDialog()

        assertNull(controller.state.value.folderDialog)
        assertTrue(controller.state.value.offerFolder)
        controller.stop()
    }

    @Test
    fun `dismissing the box withdraws the folder offer for this document`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders())
        advanceUntilIdle()

        controller.dismiss()
        story.value = state(title, longParagraph.copy(text = longParagraph.text + " More."))
        advanceUntilIdle()

        assertEquals(false, controller.state.value.offerFolder)
        assertEquals(false, controller.state.value.hasSuggestions)
        controller.stop()
    }

    @Test
    fun `when the backend cannot answer, the dialog still offers a new folder`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(folderFailure = Exception("404"))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders())
        advanceUntilIdle()

        controller.openFolderDialog()
        advanceUntilIdle()

        assertEquals(1, api.folderRequests.size)
        val dialog = controller.state.value.folderDialog
        assertNotNull(dialog)
        assertEquals(false, dialog.isLoading)
        assertTrue(dialog.options.isEmpty())
        controller.stop()
    }

    @Test
    fun `a document that opens empty is offered a folder once it has text`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi()
        val story = MutableStateFlow(state(title, paragraph.copy(text = "")))
        val controller = controller(api, story, folders = FakeFolders())
        advanceUntilIdle()
        assertEquals(false, controller.state.value.offerFolder)

        story.value = state(title, longParagraph)
        advanceUntilIdle()

        assertTrue(controller.state.value.offerFolder)
        controller.stop()
    }

    @Test
    fun `the box folds to a badge that counts the rows, and the suggestions keep coming`() = runTest(StandardTestDispatcher()) {
        val api = FakeApi(answer = listOf(WritingSuggestionDto(WritingSuggestionAction.LIST, 0.9)))
        val story = MutableStateFlow(state(title, longParagraph))
        val controller = controller(api, story, folders = FakeFolders())
        advanceUntilIdle()

        assertEquals(false, controller.collapsed.value)
        controller.toggleCollapsed()
        assertTrue(controller.collapsed.value)

        story.value = state(title, longParagraph.copy(text = longParagraph.text + " Also:"))
        advanceUntilIdle()
        story.value = state(title, longParagraph.copy(text = longParagraph.text + " Also: milk, eggs."))
        advanceUntilIdle()

        // The list and the folder offer: two rows behind the badge.
        assertEquals(2, controller.state.value.count)
        assertTrue(controller.collapsed.value)
        controller.toggleCollapsed()
        assertEquals(false, controller.collapsed.value)
        controller.stop()
    }
}
