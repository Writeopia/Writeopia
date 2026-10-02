package io.writeopia.notemenu.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The folder screen only listens to the contents of the folder shown, so the breadcrumb path
 * must be rebuilt by looking the parents up one by one.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ChooseNoteKmpViewModelFolderPathTest {

    private val notesUseCase: NotesUseCase = mockk()
    private val authRepository: AuthRepository = mockk()

    private val folders = listOf(
        folder("a", Folder.ROOT_PATH),
        folder("b", "a"),
        folder("c", "b"),
    ).associateBy { it.id }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        FolderStateController.clearInstance()

        every { authRepository.listenForUser() } returns flowOf(WriteopiaUser.disconnectedUser())
        coEvery { authRepository.getWorkspace() } returns null
        every { authRepository.listenForWorkspace() } returns flowOf(Workspace.disconnectedWorkspace())
        // Only the children of the folder shown are listened to, like on Android.
        coEvery { notesUseCase.listenForMenuItemsPerFolderId(any(), any(), any()) } returns
            flowOf(mapOf("c" to emptyList()))
        coEvery { notesUseCase.getFolderById(any()) } answers { folders[firstArg()] }
    }

    @AfterTest
    fun tearDown() {
        FolderStateController.clearInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun `inside a folder the path goes from the root down to it`() = runTest {
        val viewModel = viewModel(NotesNavigation.Folder("c"))

        val path = viewModel.folderPath.filter { it.isNotEmpty() }.first()

        assertEquals(listOf("a", "b", "c"), path.map { folder -> folder.id })
    }

    @Test
    fun `the path is empty at the root and in the favorites`() = runTest {
        listOf(NotesNavigation.Root, NotesNavigation.Favorites).forEach { navigation ->
            val viewModel = viewModel(navigation)
            viewModel.folderPath.launchIn(backgroundScope)

            assertEquals(emptyList(), viewModel.folderPath.value)
        }

        coVerify(exactly = 0) { notesUseCase.getFolderById(any()) }
    }

    private fun viewModel(navigation: NotesNavigation) = ChooseNoteKmpViewModel(
        notesUseCase = notesUseCase,
        notesConfig = mockk(),
        authRepository = authRepository,
        documentsApi = mockk(),
        selectionState = MutableStateFlow(false),
        keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE),
        workspaceConfigRepository = mockk(),
        folderSync = mockk(),
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
            workspaceId = "workspace",
            itemCount = 0,
        )
    }
}
