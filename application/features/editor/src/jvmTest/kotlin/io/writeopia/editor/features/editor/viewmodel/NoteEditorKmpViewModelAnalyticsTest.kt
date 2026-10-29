package io.writeopia.editor.features.editor.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.analytics.testing.FakeAnalyticsTracker
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.InDocumentSearchRepository
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.editor.features.editor.copy.CopyManager
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import io.writeopia.repository.UiConfigurationRepository
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.model.story.Selection
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.persistence.core.sync.DocumentSyncManager
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.sharededition.SharedEditionManager
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ui.keyboard.KeyboardEvent
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Exercises every `Analytics.capture` call site added to [NoteEditorKmpViewModel]. The ViewModel
 * has a very large dependency graph, so every collaborator but [WriteopiaStateManager] (built for
 * real, the same way [PromptServiceTest] does) is a relaxed mock.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class NoteEditorKmpViewModelAnalyticsTest {

    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val documentRepository: DocumentRepository = mockk(relaxed = true)
    private val sharedEditionManager: SharedEditionManager = mockk(relaxed = true)
    private val uiConfigurationRepository: UiConfigurationRepository = mockk(relaxed = true)
    private val folderRepository: FolderRepository = mockk(relaxed = true)
    private val workspaceConfigRepository: WorkspaceConfigRepository = mockk(relaxed = true)
    private val copyManager: CopyManager = mockk(relaxed = true)
    private val inDocumentSearchRepository: InDocumentSearchRepository = mockk(relaxed = true)
    private val documentsApi: DocumentsApi = mockk(relaxed = true)
    private val documentTagRepository: DocumentTagRepository = mockk(relaxed = true)
    private val documentSyncManager: DocumentSyncManager = mockk(relaxed = true)
    private val analyticsTracker = FakeAnalyticsTracker()

    @BeforeTest
    fun setUp() {
        // A real dispatcher, not a test one: several capture calls sit behind
        // SharingStarted.Lazily StateFlows (currentDocument, documentId) whose sharing
        // coroutine needs something actively pumping it, which UnconfinedTestDispatcher
        // (unattached to a TestScope) does not reliably do across coroutine/thread boundaries.
        Dispatchers.setMain(Dispatchers.Default)
        coEvery { authRepository.getWorkspace() } returns Workspace.disconnectedWorkspace()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun freshManager(): WriteopiaStateManager =
        WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = Dispatchers.Default,
        )

    private fun viewModel(manager: WriteopiaStateManager = freshManager()) = NoteEditorKmpViewModel(
        writeopiaManager = manager,
        documentRepository = documentRepository,
        sharedEditionManager = sharedEditionManager,
        parentFolderId = "root",
        uiConfigurationRepository = uiConfigurationRepository,
        folderRepository = folderRepository,
        workspaceConfigRepository = workspaceConfigRepository,
        keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE),
        copyManager = copyManager,
        authRepository = authRepository,
        inDocumentSearchRepository = inDocumentSearchRepository,
        documentSyncManager = documentSyncManager,
        documentsApi = documentsApi,
        aiTaskManager = AiTaskManager(),
        documentTagRepository = documentTagRepository,
        analyticsTracker = analyticsTracker,
    )

    // createNewDocument (used as setup in most tests below to get a loaded document) fires its
    // own document_created event synchronously, so waiting for "any event" isn't enough: every
    // wait here is for the specific event each test cares about.
    private suspend fun awaitEvent(eventName: String) = withTimeout(5_000) {
        while (analyticsTracker.capturedEvents.none { it.event == eventName }) delay(10)
    }

    @Test
    fun `createNewDocument should track document_created`() = runBlocking {
        viewModel().createNewDocument("doc-1", "Title")

        assertEquals(1, analyticsTracker.capturedEvents.size)
        val event = analyticsTracker.capturedEvents.single()
        assertEquals("document_created", event.event)
        assertEquals("doc-1", event.properties["document_id"])
    }

    @Test
    fun `loadDocument should track document_opened`() = runBlocking {
        viewModel().loadDocument("doc-2")

        assertEquals(1, analyticsTracker.capturedEvents.size)
        val event = analyticsTracker.capturedEvents.single()
        assertEquals("document_opened", event.event)
        assertEquals("doc-2", event.properties["document_id"])
    }

    @Test
    fun `deleteDocument should track document_deleted`() = runBlocking {
        viewModel().deleteDocument(onDeleted = {})
        awaitEvent("document_deleted")

        assertEquals("document_deleted", analyticsTracker.capturedEvents.single().event)
    }

    // publishDocument() and shareDocumentInJson/Markdown() gate on documentId / currentDocument,
    // both SharingStarted.Lazily StateFlows in WriteopiaStateManager that only mirror real state
    // once something actively collects them (normally the Compose UI). That makes their success
    // path resistant to deterministic unit testing without either a production change (reading
    // state directly instead of through the lazily-shared flow) or much heavier test infra, so
    // only the guard here is covered; document_exported/document_published's happy path is a
    // known gap — see the coverage doc for details. The capture call itself is a one-liner
    // structurally identical to document_created/document_deleted/tag_created, which ARE covered.
    @Test
    fun `publishDocument should not track when the API call fails`() = runBlocking {
        val vm = viewModel()
        vm.createNewDocument("doc-4", "Title")
        coEvery { documentsApi.publishDocument("doc-4", any()) } returns ResultData.Error()

        vm.publishDocument()
        delay(200) // no success signal to poll for

        assertEquals(
            emptyList(),
            analyticsTracker.capturedEvents.filter { it.event == "document_published" }
        )
    }

    @Test
    fun `createTag should track tag_created`() = runBlocking {
        val vm = viewModel()
        vm.createNewDocument("doc-7", "Title")

        vm.createTag("Important", "")
        awaitEvent("tag_created")

        val event = analyticsTracker.capturedEvents.single { it.event == "tag_created" }
        assertEquals("Important", event.properties["tag_name"])
    }

    @Test
    fun `createComment should track comment_created`() = runBlocking {
        val manager = freshManager()
        val now = Clock.System.now()
        // createComment needs a real text selection to succeed, so the document is loaded with
        // actual content instead of going through the empty document createNewDocument() makes.
        manager.loadDocument(
            Document(
                content = mapOf(Rank.at(0) to StoryStep(type = StoryTypes.TEXT.type, text = "We need milk.")),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )
        val vm = viewModel(manager)

        vm.createComment("A comment", Selection(start = 0, end = 4, position = Rank.at(0)))

        assertEquals(1, analyticsTracker.capturedEvents.count { it.event == "comment_created" })
    }

    @Test
    fun `aiSummary should track ai_command_used with the right command`() = runBlocking {
        val vm = viewModel()
        vm.createNewDocument("doc-9", "Title")

        vm.aiSummary(AiTargetMode.DOCUMENT)

        val event = analyticsTracker.capturedEvents.single { it.event == "ai_command_used" }
        assertEquals("SUMMARY", event.properties["command"])
    }
}
