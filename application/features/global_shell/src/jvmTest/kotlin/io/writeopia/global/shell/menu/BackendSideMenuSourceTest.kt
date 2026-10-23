package io.writeopia.global.shell.menu

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.MenuItemsRepository
import io.writeopia.core.folders.sync.ContentLoadActivity
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.response.FolderContentResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The webapp has no database: the side menu shows only what [MenuItemsRepository] fetched. */
@OptIn(ExperimentalTime::class)
class BackendSideMenuSourceTest {

    private val documentsApi: DocumentsApi = mockk()
    private val authRepository: AuthRepository = mockk()
    private val workspace = Workspace.disconnectedWorkspace().copy(id = "workspace", userId = "user")
    private val navigationUseCase = NotesNavigationUseCase.singleton()

    private val source = BackendSideMenuSource(
        menuItemsRepository = MenuItemsRepository(documentsApi),
        authRepository = authRepository,
        notesNavigationUseCase = navigationUseCase,
    )

    init {
        coEvery { authRepository.getWorkspace() } returns workspace
    }

    @AfterTest
    fun tearDown() {
        navigationUseCase.setNoteNavigation(NotesNavigation.Root)
    }

    @Test
    fun `expanding a folder shows its children`() = runTest {
        coEvery { documentsApi.getFolderContentsPreview("a", workspace.id, any()) } returns
            ResultData.Complete(FolderContentResponse(folders = listOf(folder("b", "a").toApi())))

        source.expandFolder("a")

        val items = source.menuItemsPerFolderId().first()
        assertEquals(listOf("b"), items["a"]?.map { it.id })
    }

    @Test
    fun `the side menu shows the loading while the folder is fetched`() = runTest {
        val response = CompletableDeferred<ResultData<FolderContentResponse>>()
        coEvery { documentsApi.getFolderContentsPreview("a", workspace.id, any()) } coAnswers {
            response.await()
        }

        val expanding = async { source.expandFolder("a") }
        yield()
        assertTrue(ContentLoadActivity.isLoading.value)

        response.complete(ResultData.Complete(FolderContentResponse()))
        expanding.await()
        assertFalse(ContentLoadActivity.isLoading.value)
    }

    @Test
    fun `the offline workspace asks nothing to the backend`() = runTest {
        coEvery { authRepository.getWorkspace() } returns Workspace.disconnectedWorkspace()

        source.expandFolder("a")

        coVerify(exactly = 0) { documentsApi.getFolderContentsPreview(any(), any(), any()) }
        assertTrue(source.menuItemsPerFolderId().first().isEmpty())
    }

    @Test
    fun `navigating to a folder loads it`() = runTest {
        coEvery { documentsApi.getFolderContentsPreview(any(), workspace.id, any()) } returns
            ResultData.Complete(FolderContentResponse())
        coEvery { documentsApi.getFolderContentsPreview("c", workspace.id, any()) } returns
            ResultData.Complete(FolderContentResponse(folders = listOf(folder("d", "c").toApi())))

        val following = launch { source.followNavigation() }
        navigationUseCase.setNoteNavigation(NotesNavigation.Folder("c"))

        val items = source.menuItemsPerFolderId().filter { it.containsKey("c") }.first()
        assertEquals(listOf("d"), items["c"]?.map { it.id })
        following.cancel()
    }

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
