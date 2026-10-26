package io.writeopia.notemenu.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.writeopia.analytics.testing.FakeAnalyticsTracker
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ChooseNoteKmpViewModelAnalyticsTest {

    private val notesUseCase: NotesUseCase = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val documentsApi: DocumentsApi = mockk(relaxed = true)
    private val analyticsTracker = FakeAnalyticsTracker()

    private val createdFolder = Folder(
        id = "folder-1",
        parentId = Folder.ROOT_PATH,
        title = "New folder",
        createdAt = Clock.System.now(),
        lastUpdatedAt = Clock.System.now(),
        workspaceId = "workspace",
        itemCount = 0,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        FolderStateController.clearInstance()

        every { authRepository.listenForUser() } returns flowOf(WriteopiaUser.disconnectedUser())
        coEvery { authRepository.getWorkspace() } returns Workspace.disconnectedWorkspace()
        every { authRepository.listenForWorkspace() } returns flowOf(Workspace.disconnectedWorkspace())
        coEvery {
            notesUseCase.createFolder(any(), any(), any(), any())
        } returns createdFolder
    }

    @AfterTest
    fun tearDown() {
        FolderStateController.clearInstance()
        Dispatchers.resetMain()
    }

    private fun viewModel() = ChooseNoteKmpViewModel(
        notesUseCase = notesUseCase,
        notesConfig = mockk(relaxed = true),
        authRepository = authRepository,
        documentsApi = documentsApi,
        selectionState = MutableStateFlow(false),
        keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE),
        workspaceConfigRepository = mockk(relaxed = true),
        folderSync = mockk(relaxed = true),
        notesNavigation = NotesNavigation.Root,
        analyticsTracker = analyticsTracker,
    )

    @Test
    fun `createFolderWithDetails should track folder_created with the new folder id`() = runBlocking {
        viewModel().createFolderWithDetails("New folder", icon = null)

        // createFolderWithDetails runs on Dispatchers.Default, a real dispatcher the test
        // coroutine scheduler doesn't control, so we poll for the real side effect instead.
        withTimeout(5_000) {
            while (analyticsTracker.capturedEvents.isEmpty()) delay(10)
        }

        assertEquals(1, analyticsTracker.capturedEvents.size)
        val event = analyticsTracker.capturedEvents.single()
        assertEquals("folder_created", event.event)
        assertEquals("folder-1", event.properties["folder_id"])
    }
}
