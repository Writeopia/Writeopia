@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.api.WorkspaceNewData
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class WorkspaceSyncTest {

    private val workspaceId = "testWorkspace"

    @Test
    fun `the whole workspace is asked for when it was never received`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        coEvery { folderRepository.getContentSyncedAt(WorkspaceSync.WATERMARK_ID, workspaceId) } returns null
        val (workspaceSync, documentsApi) = successfulSync(folderRepository)

        val before = Clock.System.now()
        val result = workspaceSync.syncWorkspace(workspaceId, force = true)

        assertTrue(result is ResultData.Complete)
        coVerify { documentsApi.getWorkspaceNewData(workspaceId, Instant.DISTANT_PAST, any()) }
        // The watermark is the time taken before the request, on this device's clock.
        val saved = slot<Instant>()
        coVerify { folderRepository.setContentSyncedAt(WorkspaceSync.WATERMARK_ID, workspaceId, capture(saved)) }
        assertTrue(saved.captured >= before)
    }

    @Test
    fun `only what changed since the last sync is asked for`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val syncedAt = Instant.fromEpochMilliseconds(1_000_000)
        coEvery { folderRepository.getContentSyncedAt(WorkspaceSync.WATERMARK_ID, workspaceId) } returns syncedAt
        val (workspaceSync, documentsApi) = successfulSync(folderRepository)

        workspaceSync.syncWorkspace(workspaceId, force = true)

        coVerify { documentsApi.getWorkspaceNewData(workspaceId, syncedAt, any()) }
    }

    @Test
    fun `the received documents are merged step by step and the favorites come after them`() = runBlocking {
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val received = document("doc1", "Elsewhere", parentId = "neverOpenedFolder")
        val (workspaceSync, _) = successfulSync(
            folderRepository,
            documentSyncService = documentSyncService,
            newData = WorkspaceNewData(documents = listOf(received), folders = emptyList())
        )

        workspaceSync.syncWorkspace(workspaceId, force = true)

        coVerifyOrder {
            documentSyncService.sendDeletedSteps(workspaceId)
            documentSyncService.syncDocuments(any(), workspaceId)
            documentSyncService.receiveDeletedSteps(workspaceId, any())
            documentSyncService.mergeReceivedDocuments(match { it.size == 1 && it.first().id == received.id }, any())
            // A document favorited elsewhere is on the device by now, so marking it works.
            documentSyncService.receiveFavorites(workspaceId)
        }
    }

    @Test
    fun `the watermark doesn't move while the workspace can't be received`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        coEvery { documentsApi.getWorkspaceNewData(workspaceId, any(), any()) } returns ResultData.Error()
        val workspaceSync = workspaceSync(documentsApi = documentsApi, folderRepository = folderRepository)

        val result = workspaceSync.syncWorkspace(workspaceId, force = true)

        assertTrue(result is ResultData.Error)
        coVerify(exactly = 0) { folderRepository.setContentSyncedAt(any(), any(), any()) }
    }

    @Test
    fun `nothing is received and the watermark doesn't move while the folders can't be sent`() = runBlocking {
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val (workspaceSync, _) = successfulSync(folderRepository, documentsApi, documentSyncService = documentSyncService)
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Error()

        val result = workspaceSync.syncWorkspace(workspaceId, force = true)

        assertTrue(result is ResultData.Error)
        coVerify(exactly = 0) { documentSyncService.receiveDeletedSteps(any(), any()) }
        coVerify(exactly = 0) { documentSyncService.mergeReceivedDocuments(any(), any()) }
        coVerify(exactly = 0) { folderRepository.setContentSyncedAt(any(), any(), any()) }
    }

    @Test
    fun `the outdated documents and folders of every folder are sent`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val edited = document("movedDoc", "Moved", parentId = "otherFolder")
        val movedFolder = Folder.fromName("Moved Folder", workspaceId).copy(id = "movedFolder", parentId = "otherFolder")
        coEvery { documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId) } returns listOf(edited)
        coEvery { folderRepository.localOutDatedFolders(workspaceId) } returns listOf(movedFolder)
        coEvery { folderConflictHandler.handleConflict(any(), any()) } returns listOf(movedFolder)
        val (workspaceSync, documentsApi) = successfulSync(
            folderRepository,
            documentRepository = documentRepository,
            documentSyncService = documentSyncService,
            folderConflictHandler = folderConflictHandler,
        )

        workspaceSync.syncWorkspace(workspaceId, force = true)

        coVerify { documentSyncService.syncDocuments(listOf(edited), workspaceId) }
        coVerify { documentsApi.sendFolders(listOf(movedFolder), workspaceId) }
    }

    @Test
    fun `nothing is synced for the disconnected workspace`() = runBlocking {
        val documentsApi = mockk<DocumentsApi>()
        val documentSyncService = syncService()
        val workspaceSync = workspaceSync(documentsApi = documentsApi, documentSyncService = documentSyncService)

        val result = workspaceSync.syncWorkspace("disconnected_user", force = true)

        assertTrue(result is ResultData.Complete)
        coVerify(exactly = 0) { documentsApi.getWorkspaceNewData(any(), any(), any()) }
        coVerify(exactly = 0) { documentSyncService.sendDeletedSteps(any()) }
    }

    @Test
    fun `a second sync waits for the one running instead of receiving the workspace twice`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val firstRequest = CompletableDeferred<Unit>()
        var requests = 0
        coEvery { documentsApi.getWorkspaceNewData(workspaceId, any(), any()) } coAnswers {
            requests++
            if (requests == 1) firstRequest.await()
            ResultData.Complete(WorkspaceNewData(documents = emptyList(), folders = emptyList()))
        }
        coEvery { folderRepository.localOutDatedFolders(workspaceId) } returns emptyList()
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)
        // Two instances, like the login screen's and the documents screen's.
        val first = workspaceSync(documentsApi = documentsApi, folderRepository = folderRepository)
        val second = workspaceSync(documentsApi = documentsApi, folderRepository = folderRepository)

        val firstSync = async { first.syncWorkspace(workspaceId, force = true) }
        while (requests == 0) yield()
        val secondSync = async { second.syncWorkspace(workspaceId, force = true) }
        repeat(10) { yield() }
        // The second has not asked the backend: the first still holds the sync.
        assertEquals(1, requests)

        firstRequest.complete(Unit)
        assertTrue(firstSync.await() is ResultData.Complete)
        assertTrue(secondSync.await() is ResultData.Complete)
        assertEquals(2, requests)
    }

    @Test
    fun `a sync right after another is skipped unless forced, whichever instance synced`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val (first, documentsApi) = successfulSync(folderRepository, minSyncInternal = 10_000.milliseconds)
        val second = workspaceSync(
            documentsApi = documentsApi,
            folderRepository = folderRepository,
            minSyncInternal = 10_000.milliseconds
        )

        assertTrue(first.syncWorkspace(workspaceId, force = true) is ResultData.Complete)
        assertTrue(second.syncWorkspace(workspaceId) is ResultData.Idle)
        assertFalse(second.syncWorkspace(workspaceId, force = true) is ResultData.Idle)

        coVerify(exactly = 2) { documentsApi.getWorkspaceNewData(workspaceId, any(), any()) }
    }

    private fun document(
        id: String,
        title: String,
        parentId: String = Folder.ROOT_PATH,
    ) = Document(
        id = id,
        title = title,
        createdAt = Instant.DISTANT_PAST,
        lastUpdatedAt = Instant.DISTANT_PAST,
        lastSyncedAt = null,
        workspaceId = workspaceId,
        parentId = parentId
    )

    /** A step sync that succeeds at everything and receives nothing. */
    private fun syncService(): DocumentSyncService = mockk<DocumentSyncService>(relaxed = true).also { service ->
        coEvery { service.sendDeletedSteps(any()) } returns emptySet()
        coEvery { service.syncDocuments(any(), any()) } returns emptyList()
        coEvery { service.receiveDeletedSteps(any(), any()) } returns
            DocumentSyncService.ReceivedDeletions(emptySet(), Instant.fromEpochMilliseconds(0))
        coEvery { service.receiveFavorites(any()) } returns true
    }

    private fun workspaceSync(
        documentRepository: DocumentRepository = mockk(relaxed = true),
        documentsApi: DocumentsApi,
        documentSyncService: DocumentSyncService = syncService(),
        folderConflictHandler: FolderConflictHandler = mockk<FolderConflictHandler>().also {
            coEvery { it.handleConflict(any(), any()) } returns emptyList()
        },
        folderRepository: FolderRepository = mockk(relaxed = true),
        minSyncInternal: kotlin.time.Duration = 0.milliseconds,
    ): WorkspaceSync = WorkspaceSync(
        folderRepository = folderRepository,
        documentRepository = documentRepository,
        documentsApi = documentsApi,
        documentSyncService = documentSyncService,
        folderConflictHandler = folderConflictHandler,
        imageSync = mockk(relaxed = true),
        minSyncInternal = minSyncInternal,
    )

    private fun successfulSync(
        folderRepository: FolderRepository,
        documentsApi: DocumentsApi = mockk(),
        documentRepository: DocumentRepository = mockk(relaxed = true),
        documentSyncService: DocumentSyncService = syncService(),
        folderConflictHandler: FolderConflictHandler = mockk<FolderConflictHandler>().also {
            coEvery { it.handleConflict(any(), any()) } returns emptyList()
        },
        newData: WorkspaceNewData = WorkspaceNewData(documents = emptyList(), folders = emptyList()),
        minSyncInternal: kotlin.time.Duration = 0.milliseconds,
    ): Pair<WorkspaceSync, DocumentsApi> {
        coEvery { documentsApi.getWorkspaceNewData(workspaceId, any(), any()) } returns ResultData.Complete(newData)
        coEvery { folderRepository.localOutDatedFolders(workspaceId) } returns emptyList()
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)

        val workspaceSync = workspaceSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentSyncService = documentSyncService,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
            minSyncInternal = minSyncInternal,
        )
        return workspaceSync to documentsApi
    }
}
