package io.writeopia.notemenu.viewmodel.onlybe

import io.mockk.coEvery
import io.mockk.mockk
import io.writeopia.analytics.testing.FakeAnalyticsTracker
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.MenuItemsRepository
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.response.FolderContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
class OnlyBackendChooseNoteKmpViewModelAnalyticsTest {

    private val documentsApi: DocumentsApi = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val workspace = Workspace.disconnectedWorkspace()
    private val analyticsTracker = FakeAnalyticsTracker()

    private val createdFolder = Folder(
        id = "folder-1",
        parentId = Folder.ROOT_PATH,
        title = "New folder",
        createdAt = Clock.System.now(),
        lastUpdatedAt = Clock.System.now(),
        workspaceId = workspace.id,
        itemCount = 0,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery { documentsApi.getFolderContentsPreview(any(), any(), any()) } returns
            ResultData.Complete(FolderContentResponse())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(navigation: NotesNavigation = NotesNavigation.Root) =
        OnlyBackendChooseNoteKmpViewModel(
            documentsApi = documentsApi,
            authRepository = authRepository,
            menuItemsRepository = MenuItemsRepository(documentsApi),
            notesNavigation = navigation,
            analyticsTracker = analyticsTracker,
        )

    // createFolderWithDetails/addFolder run on Dispatchers.Default, a real dispatcher the test
    // coroutine scheduler doesn't control, so we poll for the real side effect instead of using
    // virtual-time helpers like advanceUntilIdle.
    private suspend fun awaitCapturedEvent() = withTimeout(5_000) {
        while (analyticsTracker.capturedEvents.isEmpty()) delay(10)
    }

    @Test
    fun `createFolderWithDetails should track folder_created with the new folder id`() = runBlocking {
        coEvery {
            documentsApi.createFolder(any(), any(), any(), any())
        } returns ResultData.Complete(createdFolder)

        viewModel().createFolderWithDetails("New folder", icon = null)
        awaitCapturedEvent()

        assertEquals(1, analyticsTracker.capturedEvents.size)
        val event = analyticsTracker.capturedEvents.single()
        assertEquals("folder_created", event.event)
        assertEquals("folder-1", event.properties["folder_id"])
    }

    @Test
    fun `createFolderWithDetails should not track when the API call fails`() = runBlocking {
        coEvery {
            documentsApi.createFolder(any(), any(), any(), any())
        } returns ResultData.Error()

        viewModel().createFolderWithDetails("New folder", icon = null)
        delay(200) // let the launched coroutine settle; there is no success signal to poll for

        assertEquals(emptyList(), analyticsTracker.capturedEvents)
    }

    @Test
    fun `addFolder should track folder_created with the new folder id`() = runBlocking {
        coEvery {
            documentsApi.createFolder(any(), any(), any(), any())
        } returns ResultData.Complete(createdFolder)

        viewModel().addFolder(Folder.ROOT_PATH)
        awaitCapturedEvent()

        assertEquals(1, analyticsTracker.capturedEvents.size)
        val event = analyticsTracker.capturedEvents.single()
        assertEquals("folder_created", event.event)
        assertEquals("folder-1", event.properties["folder_id"])
    }
}
