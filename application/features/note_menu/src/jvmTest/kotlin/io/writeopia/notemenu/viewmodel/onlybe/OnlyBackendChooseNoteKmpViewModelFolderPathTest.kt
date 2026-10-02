package io.writeopia.notemenu.viewmodel.onlybe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.MenuItemsRepository
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.response.FolderContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * In the backend only mode the breadcrumb comes from the backend, so it's complete even when
 * the app starts straight inside a folder and no parent was visited before.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class OnlyBackendChooseNoteKmpViewModelFolderPathTest {

    private val documentsApi: DocumentsApi = mockk()
    private val authRepository: AuthRepository = mockk()
    private val workspace = Workspace.disconnectedWorkspace()
    private val path = listOf(folder("a", Folder.ROOT_PATH), folder("b", "a"), folder("c", "b"))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery { documentsApi.getFolderContents(any(), any()) } returns
            ResultData.Complete(FolderContentResponse())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `inside a folder the path is asked to the backend`() = runBlocking {
        coEvery { documentsApi.getFolderPath("c", workspace.id) } returns ResultData.Complete(path)

        val viewModel = viewModel(NotesNavigation.Folder("c"))
        val result = withTimeout(5_000) { viewModel.folderPath.filter { it.isNotEmpty() }.first() }

        assertEquals(listOf("a", "b", "c"), result.map { folder -> folder.id })
    }

    @Test
    fun `when the backend fails the path falls back to the folders already visited`() = runBlocking {
        coEvery { documentsApi.getFolderPath("c", workspace.id) } returns ResultData.Error()
        val repository = MenuItemsRepository(documentsApi)
        coEvery { documentsApi.getFolderContents("b", workspace.id) } returns
            ResultData.Complete(FolderContentResponse(folders = listOf(path[2].toApi())))
        coEvery { documentsApi.getFolderContents("a", workspace.id) } returns
            ResultData.Complete(FolderContentResponse(folders = listOf(path[1].toApi())))
        coEvery { documentsApi.getFolderContents(Folder.ROOT_PATH, workspace.id) } returns
            ResultData.Complete(FolderContentResponse(folders = listOf(path[0].toApi())))
        // The user opened a, then b, before landing in c.
        repository.loadFolderContents(Folder.ROOT_PATH, workspace.id)
        repository.loadFolderContents("a", workspace.id)
        repository.loadFolderContents("b", workspace.id)

        val viewModel = viewModel(NotesNavigation.Folder("c"), repository)
        val result = withTimeout(5_000) { viewModel.folderPath.filter { it.isNotEmpty() }.first() }

        assertEquals(listOf("a", "b", "c"), result.map { folder -> folder.id })
    }

    @Test
    fun `the root and the favorites never ask for a path`() = runBlocking {
        viewModel(NotesNavigation.Root)
        viewModel(NotesNavigation.Favorites)

        coVerify(exactly = 0) { documentsApi.getFolderPath(any(), any()) }
    }

    private fun viewModel(
        navigation: NotesNavigation,
        repository: MenuItemsRepository = MenuItemsRepository(documentsApi),
    ) = OnlyBackendChooseNoteKmpViewModel(
        documentsApi = documentsApi,
        authRepository = authRepository,
        menuItemsRepository = repository,
        notesNavigation = navigation,
    )

    private fun folder(id: String, parentId: String): Folder {
        val now = Clock.System.now()
        return Folder(
            id = id,
            parentId = parentId,
            title = "Title $id",
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = workspace.id,
            itemCount = 0,
        )
    }
}
