@file:OptIn(ExperimentalTime::class)

package io.writeopia.notemenu.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class)
class FolderStateControllerTest {

    private var testScope: CoroutineScope? = null

    private val workspaceId = "testWorkspace"
    private val authToken = "testToken"

    @AfterTest
    fun tearDown() {
        testScope?.cancel()
        // Reset singleton
        FolderStateController.instance = null
    }

    @Test
    fun `addFolder should sync folder to backend after creation for premium users`() = runTest {
        val notesUseCase = mockk<NotesUseCase>()
        val authRepository = mockk<AuthRepository>()
        val documentsApi = mockk<DocumentsApi>()

        val createdFolder = Folder.fromName("Untitled", workspaceId).copy(
            id = "newFolderId",
            parentId = "parentId"
        )

        val workspace = Workspace(
            id = workspaceId,
            name = "Test Workspace",
            ownerId = "ownerId"
        )

        val premiumUser = WriteopiaUser(
            id = "userId",
            name = "Test User",
            email = "test@example.com",
            tier = Tier.PREMIUM
        )

        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery { authRepository.isLoggedIn() } returns true
        coEvery { authRepository.getUser() } returns premiumUser
        coEvery { authRepository.getAuthToken() } returns authToken
        coEvery { notesUseCase.createFolder(any(), any(), any(), any()) } returns createdFolder
        coEvery { documentsApi.sendFolders(any(), any(), any()) } returns ResultData.Complete(Unit)

        val controller = FolderStateController.singleton(notesUseCase, authRepository, documentsApi)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        testScope = scope
        controller.initCoroutine(scope)

        // Execute
        controller.addFolder("parentId")
        advanceUntilIdle()

        // Verify folder was created
        coVerify { notesUseCase.createFolder("Untitled", workspaceId, "parentId", null) }

        // Verify folder was synced to backend
        val foldersSlot = slot<List<Folder>>()
        coVerify { documentsApi.sendFolders(capture(foldersSlot), workspaceId, authToken) }
        assertEquals(1, foldersSlot.captured.size)
        assertEquals(createdFolder.id, foldersSlot.captured.first().id)
    }

    @Test
    fun `addFolder should not sync to backend for non-premium users`() = runTest {
        val notesUseCase = mockk<NotesUseCase>()
        val authRepository = mockk<AuthRepository>()
        val documentsApi = mockk<DocumentsApi>()

        val createdFolder = Folder.fromName("Untitled", workspaceId).copy(
            id = "newFolderId",
            parentId = "parentId"
        )

        val workspace = Workspace(
            id = workspaceId,
            name = "Test Workspace",
            ownerId = "ownerId"
        )

        val freeUser = WriteopiaUser(
            id = "userId",
            name = "Test User",
            email = "test@example.com",
            tier = Tier.FREE
        )

        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery { authRepository.isLoggedIn() } returns true
        coEvery { authRepository.getUser() } returns freeUser
        coEvery { notesUseCase.createFolder(any(), any(), any(), any()) } returns createdFolder

        val controller = FolderStateController.singleton(notesUseCase, authRepository, documentsApi)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        testScope = scope
        controller.initCoroutine(scope)

        // Execute
        controller.addFolder("parentId")
        advanceUntilIdle()

        // Verify folder was created
        coVerify { notesUseCase.createFolder("Untitled", workspaceId, "parentId", null) }

        // Verify NO sync to backend (free user)
        coVerify(exactly = 0) { documentsApi.sendFolders(any(), any(), any()) }
    }

    @Test
    fun `addFolder should not sync to backend when not logged in`() = runTest {
        val notesUseCase = mockk<NotesUseCase>()
        val authRepository = mockk<AuthRepository>()
        val documentsApi = mockk<DocumentsApi>()

        val createdFolder = Folder.fromName("Untitled", workspaceId).copy(
            id = "newFolderId",
            parentId = "parentId"
        )

        val workspace = Workspace(
            id = workspaceId,
            name = "Test Workspace",
            ownerId = "ownerId"
        )

        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery { authRepository.isLoggedIn() } returns false
        coEvery { notesUseCase.createFolder(any(), any(), any(), any()) } returns createdFolder

        val controller = FolderStateController.singleton(notesUseCase, authRepository, documentsApi)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        testScope = scope
        controller.initCoroutine(scope)

        // Execute
        controller.addFolder("parentId")
        advanceUntilIdle()

        // Verify folder was created locally
        coVerify { notesUseCase.createFolder("Untitled", workspaceId, "parentId", null) }

        // Verify NO sync to backend (not logged in)
        coVerify(exactly = 0) { documentsApi.sendFolders(any(), any(), any()) }
    }

    @Test
    fun `updateFolder should sync folder to backend after update for premium users`() = runTest {
        val notesUseCase = mockk<NotesUseCase>(relaxed = true)
        val authRepository = mockk<AuthRepository>()
        val documentsApi = mockk<DocumentsApi>()

        val folderToUpdate = Folder.fromName("Updated Name", workspaceId).copy(
            id = "folderId"
        )

        val workspace = Workspace(
            id = workspaceId,
            name = "Test Workspace",
            ownerId = "ownerId"
        )

        val premiumUser = WriteopiaUser(
            id = "userId",
            name = "Test User",
            email = "test@example.com",
            tier = Tier.PREMIUM
        )

        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery { authRepository.isLoggedIn() } returns true
        coEvery { authRepository.getUser() } returns premiumUser
        coEvery { authRepository.getAuthToken() } returns authToken
        coEvery { documentsApi.sendFolders(any(), any(), any()) } returns ResultData.Complete(Unit)

        val controller = FolderStateController.singleton(notesUseCase, authRepository, documentsApi)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        testScope = scope
        controller.initCoroutine(scope)

        // Execute
        controller.updateFolder(folderToUpdate)
        advanceUntilIdle()

        // Verify folder was updated locally
        coVerify { notesUseCase.updateFolder(match { it.id == folderToUpdate.id }) }

        // Verify folder was synced to backend
        coVerify { documentsApi.sendFolders(match { it.size == 1 && it.first().id == folderToUpdate.id }, workspaceId, authToken) }
    }

    @Test
    fun `deleteFolder should sync deletion to backend for premium users`() = runTest {
        val notesUseCase = mockk<NotesUseCase>(relaxed = true)
        val authRepository = mockk<AuthRepository>()
        val documentsApi = mockk<DocumentsApi>()

        val folderId = "folderToDelete"

        val workspace = Workspace(
            id = workspaceId,
            name = "Test Workspace",
            ownerId = "ownerId"
        )

        val premiumUser = WriteopiaUser(
            id = "userId",
            name = "Test User",
            email = "test@example.com",
            tier = Tier.PREMIUM
        )

        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery { authRepository.isLoggedIn() } returns true
        coEvery { authRepository.getUser() } returns premiumUser
        coEvery { authRepository.getAuthToken() } returns authToken
        coEvery { documentsApi.deleteFolder(any(), any(), any()) } returns ResultData.Complete(Unit)

        val controller = FolderStateController.singleton(notesUseCase, authRepository, documentsApi)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        testScope = scope
        controller.initCoroutine(scope)

        // Execute
        controller.deleteFolder(folderId)
        advanceUntilIdle()

        // Verify folder was deleted locally
        coVerify { notesUseCase.deleteFolderById(folderId) }

        // Verify deletion was synced to backend
        coVerify { documentsApi.deleteFolder(folderId, workspaceId, authToken) }
    }
}
