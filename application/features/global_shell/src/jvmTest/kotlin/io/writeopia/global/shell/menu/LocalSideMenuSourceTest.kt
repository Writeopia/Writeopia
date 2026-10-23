package io.writeopia.global.shell.menu

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * With Room each folder is listened to on its own, so the children of an expanded folder only
 * show if the side menu listens to that folder too.
 */
@OptIn(ExperimentalTime::class)
class LocalSideMenuSourceTest {

    private val notesUseCase: NotesUseCase = mockk()
    private val authRepository: AuthRepository = mockk()
    private val workspace = Workspace.disconnectedWorkspace()
    private val user = WriteopiaUser.disconnectedUser()

    private val source = LocalSideMenuSource(
        notesUseCase = notesUseCase,
        authRepository = authRepository,
        notesNavigationUseCase = NotesNavigationUseCase.singleton(),
        folderSync = null,
    )

    init {
        every { authRepository.listenForUser() } returns flowOf(user)
        every { authRepository.listenForWorkspace() } returns flowOf(workspace)
        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery {
            notesUseCase.listenForMenuItemsPerFolderId(NotesNavigation.Root, user.id, workspace.id)
        } returns flowOf(mapOf(Folder.ROOT_PATH to listOf(folder("a", Folder.ROOT_PATH))))
        coEvery {
            notesUseCase.listenForMenuItemsPerFolderId(
                match { navigation -> navigation is NotesNavigation.Folder && navigation.id == "a" },
                user.id,
                workspace.id
            )
        } returns flowOf(mapOf("a" to listOf(folder("b", "a"))))
    }

    @Test
    fun `expanding a folder shows its children next to the folder navigated to`() = runTest {
        source.expandFolder("a")

        val items = source.menuItemsPerFolderId().filter { it.containsKey("a") }.first()

        assertEquals(listOf("a"), items[Folder.ROOT_PATH]?.map { it.id })
        assertEquals(listOf("b"), items["a"]?.map { it.id })
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
