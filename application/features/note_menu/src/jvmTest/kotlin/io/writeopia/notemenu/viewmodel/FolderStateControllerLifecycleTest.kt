package io.writeopia.notemenu.viewmodel

import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * On mobile every folder screen has its own ViewModel while the folder controller is shared.
 * Leaving a folder clears that ViewModel, which must not stop the screens that remain from
 * changing folders.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class FolderStateControllerLifecycleTest {

    private val notesUseCase: NotesUseCase = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        FolderStateController.clearInstance()

        every { authRepository.listenForUser() } returns flowOf(WriteopiaUser.disconnectedUser())
        every { authRepository.listenForWorkspace() } returns flowOf(Workspace.disconnectedWorkspace())
        coEvery { authRepository.getWorkspace() } returns null
        coEvery { authRepository.isLoggedIn() } returns false
        coEvery { notesUseCase.listenForMenuItemsPerFolderId(any(), any(), any()) } returns
            flowOf(emptyMap())
        coEvery { notesUseCase.updateFolderById(any(), any()) } answers {
            secondArg<(Folder) -> Folder>().invoke(folder("a"))
        }
    }

    @AfterTest
    fun tearDown() {
        FolderStateController.clearInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun `a folder icon can still be changed after leaving another folder`() = runTest {
        val rootViewModel = viewModel(NotesNavigation.Root)
        val folderViewModel = viewModel(NotesNavigation.Folder("a"))

        // Going back from the folder clears its ViewModel.
        folderViewModel.viewModelScope.cancel()

        rootViewModel.changeIcons("a", "star", 0xFF0000FF.toInt(), IconChange.FOLDER)

        coVerify(timeout = 2_000) { notesUseCase.updateFolderById("a", any()) }
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

    private fun folder(id: String): Folder {
        val now = Clock.System.now()
        return Folder(
            id = id,
            parentId = Folder.ROOT_PATH,
            title = "Title $id",
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = "workspace",
            itemCount = 0,
        )
    }
}
