@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.response.FolderContentResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FolderSyncTest {

    private val workspaceId = "testWorkspace"
    private val folderId = "testFolder"

    @Test
    fun `syncFolder should fetch and sync both documents and subfolders`() = runBlocking {
        // Setup mocks
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val documentConflictHandler = mockk<DocumentConflictHandler>()
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val authRepository = mockk<AuthRepository>()

        val existingFolder = Folder.fromName("Test Folder", workspaceId).copy(
            id = folderId,
            lastSyncedAt = Instant.DISTANT_PAST
        )

        val newDocument = Document(
            id = "doc1",
            title = "New Document",
            createdAt = Instant.DISTANT_PAST,
            lastUpdatedAt = Instant.DISTANT_PAST,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = folderId
        )

        val newSubfolder = Folder.fromName("Subfolder", workspaceId).copy(
            id = "subfolder1",
            parentId = folderId
        )

        val folderContentResponse = FolderContentResponse(
            documents = listOf(newDocument.toApi()),
            folders = listOf(newSubfolder.toApi())
        )

        // Mock API to return both documents and subfolders
        coEvery { folderRepository.getFolderById(folderId) } returns existingFolder
        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Complete(folderContentResponse)

        coEvery { documentRepository.loadOutdatedDocumentsByFolder(folderId, workspaceId) } returns emptyList()
        coEvery { folderRepository.getFolderByParentId(folderId, workspaceId) } returns emptyList()

        coEvery { documentConflictHandler.handleConflict(any(), any()) } returns emptyList()
        coEvery { folderConflictHandler.handleConflict(any(), any()) } returns emptyList()

        coEvery { documentsApi.sendDocuments(any(), workspaceId) } returns ResultData.Complete(Unit)
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)

        val folderSync = FolderSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentConflictHandler = documentConflictHandler,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
            authRepository = authRepository,
            minSyncInternal = 0.milliseconds
        )

        // Execute
        folderSync.syncFolder(folderId, workspaceId, force = true)

        // Verify that getFolderNewData was called (now returns both docs and folders)
        coVerify { documentsApi.getFolderNewData(folderId, workspaceId, any(), any()) }

        // Verify that conflict handlers were called for both documents and folders
        coVerify { documentConflictHandler.handleConflict(any(), match { it.size == 1 && it.first().id == newDocument.id }) }
        coVerify { folderConflictHandler.handleConflict(any(), match { it.size == 1 && it.first().id == newSubfolder.id }) }

        // Verify that both sendDocuments and sendFolders were called
        coVerify { documentsApi.sendDocuments(any(), workspaceId) }
        coVerify { documentsApi.sendFolders(any(), workspaceId) }
    }

    @Test
    fun `syncFolder should handle local outdated subfolders`() = runBlocking {
        // Setup mocks
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val documentConflictHandler = mockk<DocumentConflictHandler>()
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val authRepository = mockk<AuthRepository>()

        val existingFolder = Folder.fromName("Test Folder", workspaceId).copy(
            id = folderId,
            lastSyncedAt = Instant.DISTANT_PAST
        )

        val localSubfolder = Folder.fromName("Local Subfolder", workspaceId).copy(
            id = "localSubfolder",
            parentId = folderId
        )

        // Mock API to return empty (no new data from backend)
        coEvery { folderRepository.getFolderById(folderId) } returns existingFolder
        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Complete(FolderContentResponse())

        coEvery { documentRepository.loadOutdatedDocumentsByFolder(folderId, workspaceId) } returns emptyList()
        coEvery { folderRepository.getFolderByParentId(folderId, workspaceId) } returns listOf(localSubfolder)

        coEvery { documentConflictHandler.handleConflict(any(), any()) } returns emptyList()

        // Local subfolder should be marked as not sent (needs to sync to backend)
        val foldersNotSentSlot = slot<List<Folder>>()
        coEvery {
            folderConflictHandler.handleConflict(capture(foldersNotSentSlot), any())
        } returns listOf(localSubfolder)

        coEvery { documentsApi.sendDocuments(any(), workspaceId) } returns ResultData.Complete(Unit)
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)

        val folderSync = FolderSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentConflictHandler = documentConflictHandler,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
            authRepository = authRepository,
            minSyncInternal = 0.milliseconds
        )

        // Execute
        folderSync.syncFolder(folderId, workspaceId, force = true)

        // Verify local subfolders were passed to conflict handler
        assertEquals(listOf(localSubfolder), foldersNotSentSlot.captured)

        // Verify sendFolders was called with the local subfolder
        coVerify { documentsApi.sendFolders(listOf(localSubfolder), workspaceId) }
    }

    @Test
    fun `syncFolder should not sync for disconnected workspace`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val documentConflictHandler = mockk<DocumentConflictHandler>()
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val authRepository = mockk<AuthRepository>()

        val folderSync = FolderSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentConflictHandler = documentConflictHandler,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
            authRepository = authRepository,
            minSyncInternal = 0.milliseconds
        )

        // Execute with disconnected workspace ID
        folderSync.syncFolder(folderId, "disconnected_user", force = true)

        // Verify no API calls were made
        coVerify(exactly = 0) { documentsApi.getFolderNewData(any(), any(), any(), any()) }
        coVerify(exactly = 0) { documentsApi.sendFolders(any(), any()) }
    }

    @Test
    fun `syncFolder should send documents and refresh on success without updating local timestamps`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val documentConflictHandler = mockk<DocumentConflictHandler>()
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val authRepository = mockk<AuthRepository>()

        val existingFolder = Folder.fromName("Test Folder", workspaceId).copy(
            id = folderId,
            lastSyncedAt = Instant.DISTANT_PAST
        )

        val documentToSend = Document(
            id = "doc1",
            title = "Document to Send",
            createdAt = Instant.DISTANT_PAST,
            lastUpdatedAt = Instant.DISTANT_PAST,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = folderId
        )

        coEvery { folderRepository.getFolderById(folderId) } returns existingFolder
        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Complete(FolderContentResponse())

        coEvery { documentRepository.loadOutdatedDocumentsByFolder(folderId, workspaceId) } returns emptyList()
        coEvery { folderRepository.getFolderByParentId(folderId, workspaceId) } returns emptyList()

        coEvery { documentConflictHandler.handleConflict(any(), any()) } returns listOf(documentToSend)
        coEvery { folderConflictHandler.handleConflict(any(), any()) } returns emptyList()

        coEvery { documentsApi.sendDocuments(any(), workspaceId) } returns ResultData.Complete(Unit)
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)

        val folderSync = FolderSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentConflictHandler = documentConflictHandler,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
            authRepository = authRepository,
            minSyncInternal = 0.milliseconds
        )

        // Execute
        folderSync.syncFolder(folderId, workspaceId, force = true)

        // Verify documents were sent to the backend
        coVerify { documentsApi.sendDocuments(listOf(documentToSend), workspaceId) }

        // Verify NO local timestamp updates - lastSyncedAt must come from server
        coVerify(exactly = 0) { documentRepository.saveDocumentMetadata(any()) }
        coVerify(exactly = 0) { folderRepository.updateFolder(any()) }

        // Verify refresh was called (twice: once after conflict resolution, once after sending)
        coVerify(atLeast = 2) { documentRepository.refreshDocuments() }
        coVerify(atLeast = 2) { folderRepository.refreshFolders() }
    }

    @Test
    fun `syncFolder should not update timestamps when sendFolders fails`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val documentConflictHandler = mockk<DocumentConflictHandler>()
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val authRepository = mockk<AuthRepository>()

        val existingFolder = Folder.fromName("Test Folder", workspaceId).copy(
            id = folderId,
            lastSyncedAt = Instant.DISTANT_PAST
        )

        coEvery { folderRepository.getFolderById(folderId) } returns existingFolder
        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Complete(FolderContentResponse())

        coEvery { documentRepository.loadOutdatedDocumentsByFolder(folderId, workspaceId) } returns emptyList()
        coEvery { folderRepository.getFolderByParentId(folderId, workspaceId) } returns emptyList()

        coEvery { documentConflictHandler.handleConflict(any(), any()) } returns emptyList()
        coEvery { folderConflictHandler.handleConflict(any(), any()) } returns emptyList()

        coEvery { documentsApi.sendDocuments(any(), workspaceId) } returns ResultData.Complete(Unit)
        // sendFolders fails
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Error()

        val folderSync = FolderSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentConflictHandler = documentConflictHandler,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
            authRepository = authRepository,
            minSyncInternal = 0.milliseconds
        )

        // Execute
        folderSync.syncFolder(folderId, workspaceId, force = true)

        // Verify folder sync time was NOT updated (because sendFolders failed)
        coVerify(exactly = 0) { folderRepository.updateFolder(match { it.id == folderId }) }
    }
}
