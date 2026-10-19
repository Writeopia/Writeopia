@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.tag.InMemoryDocumentTagRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.response.FolderContentResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FolderSyncTest {

    private val workspaceId = "testWorkspace"
    private val folderId = "testFolder"

    @Test
    fun `syncFolder should fetch and sync both documents and subfolders`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val documentSyncService = syncService()
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)

        val newDocument = document("doc1", "New Document")
        val newSubfolder = Folder.fromName("Subfolder", workspaceId).copy(
            id = "subfolder1",
            parentId = folderId
        )

        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Complete(
            FolderContentResponse(
                documents = listOf(newDocument.toApi()),
                folders = listOf(newSubfolder.toApi())
            )
        )
        coEvery { documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId) } returns emptyList()
        coEvery { folderRepository.localOutDatedFolders(workspaceId) } returns emptyList()
        coEvery { folderConflictHandler.handleConflict(any(), any()) } returns emptyList()
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)

        val folderSync = folderSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentSyncService = documentSyncService,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
        )

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify { documentsApi.getFolderNewData(folderId, workspaceId, any(), any()) }
        // The received documents are merged step by step, never saved whole.
        coVerify { documentSyncService.mergeReceivedDocuments(match { it.size == 1 && it.first().id == newDocument.id }, any()) }
        coVerify(exactly = 0) { documentRepository.saveDocument(any()) }
        coVerify { folderConflictHandler.handleConflict(any(), match { it.size == 1 && it.first().id == newSubfolder.id }) }
        coVerify { documentSyncService.syncDocuments(any(), workspaceId) }
        coVerify { documentsApi.sendFolders(any(), workspaceId) }
    }

    @Test
    fun `syncFolder applies the tag catalogue and sends the outdated tags`() = runBlocking {
        val documentsApi = mockk<DocumentsApi>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val tagRepository = InMemoryDocumentTagRepository()

        val localTag = DocumentTag.create("Local", "Made here", workspaceId)
        tagRepository.upsertTag(localTag)
        val remoteTag = DocumentTag.create("Remote", "Made elsewhere", workspaceId).copy(id = "remote-tag")

        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Complete(FolderContentResponse(tags = listOf(remoteTag.toApi())))
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)
        coEvery { documentsApi.sendTags(any(), workspaceId) } returns ResultData.Complete(Unit)

        val folderSync = folderSync(
            documentsApi = documentsApi,
            folderRepository = folderRepository,
            documentTagRepository = tagRepository,
            documentTagConflictHandler = DocumentTagConflictHandler(tagRepository),
        )

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify { documentsApi.sendTags(match { it.size == 1 && it.first().id == localTag.id }, workspaceId) }
        assertEquals("Remote", tagRepository.getTagById("remote-tag")?.name)
        assertNotNull(tagRepository.getTagById(localTag.id)?.lastSyncedAt)
        Unit
    }

    @Test
    fun `syncFolder should handle local outdated subfolders`() = runBlocking {
        val documentsApi = mockk<DocumentsApi>()
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)

        val localSubfolder = Folder.fromName("Local Subfolder", workspaceId).copy(
            id = "localSubfolder",
            parentId = folderId,
            lastSyncedAt = null
        )

        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Complete(FolderContentResponse())
        coEvery { folderRepository.localOutDatedFolders(workspaceId) } returns listOf(localSubfolder)
        val foldersNotSentSlot = slot<List<Folder>>()
        coEvery { folderConflictHandler.handleConflict(capture(foldersNotSentSlot), any()) } returns listOf(localSubfolder)
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)

        val folderSync = folderSync(
            documentsApi = documentsApi,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
        )

        folderSync.syncFolder(folderId, workspaceId, force = true)

        assertEquals(listOf(localSubfolder), foldersNotSentSlot.captured)
        coVerify { documentsApi.sendFolders(listOf(localSubfolder), workspaceId) }
    }

    @Test
    fun `syncFolder sends outdated items of other folders too`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val documentSyncService = syncService()
        val folderConflictHandler = mockk<FolderConflictHandler>()
        val folderRepository = mockk<FolderRepository>(relaxed = true)

        // Moved into another folder while offline: outdated, but not inside the synced folder.
        val movedDocument = document("movedDoc", "Moved", parentId = "otherFolder")
        val movedFolder = Folder.fromName("Moved Folder", workspaceId).copy(
            id = "movedFolder",
            parentId = "otherFolder"
        )

        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Complete(FolderContentResponse())
        coEvery { documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId) } returns listOf(movedDocument)
        coEvery { folderRepository.localOutDatedFolders(workspaceId) } returns listOf(movedFolder)
        val foldersSlot = slot<List<Folder>>()
        coEvery { folderConflictHandler.handleConflict(capture(foldersSlot), any()) } returns listOf(movedFolder)
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)

        val folderSync = folderSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentSyncService = documentSyncService,
            folderConflictHandler = folderConflictHandler,
            folderRepository = folderRepository,
        )

        folderSync.syncFolder(folderId, workspaceId, force = true)

        // Wherever it is, it goes with this sync, through the step sync.
        coVerify { documentSyncService.syncDocuments(listOf(movedDocument), workspaceId) }
        assertEquals(listOf(movedFolder), foldersSlot.captured)
        coVerify { documentsApi.sendFolders(listOf(movedFolder), workspaceId) }
        // Only the opened folder is received.
        coVerify(exactly = 1) { documentsApi.getFolderNewData(any(), any(), any(), any()) }
    }

    @Test
    fun `syncFolder should not sync for disconnected workspace`() = runBlocking {
        val documentsApi = mockk<DocumentsApi>()
        val documentSyncService = syncService()

        val folderSync = folderSync(documentsApi = documentsApi, documentSyncService = documentSyncService)

        folderSync.syncFolder(folderId, "disconnected_user", force = true)

        coVerify(exactly = 0) { documentsApi.getFolderNewData(any(), any(), any(), any()) }
        coVerify(exactly = 0) { documentsApi.sendFolders(any(), any()) }
        coVerify(exactly = 0) { documentSyncService.sendDeletedSteps(any()) }
    }

    @Test
    fun `the deletions go first, then the outdated documents, then what changed elsewhere`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val edited = document("doc1", "Edited")
        coEvery { documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId) } returns listOf(edited)
        val (folderSync, _) = successfulSync(
            folderRepository,
            documentRepository = documentRepository,
            documentSyncService = documentSyncService
        )

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerifyOrder {
            documentSyncService.sendDeletedSteps(workspaceId)
            documentSyncService.syncDocuments(listOf(edited), workspaceId)
            documentSyncService.receiveDeletedSteps(workspaceId, any())
            documentSyncService.mergeReceivedDocuments(any(), any())
        }
        // The sync never writes a document itself: stamping is the step sync's job.
        coVerify(exactly = 0) { documentRepository.saveDocumentMetadata(any()) }
        coVerify(exactly = 0) { documentRepository.saveDocument(any()) }
    }

    @Test
    fun `nothing is received and the watermark doesn't move while the documents can't be sent`() = runBlocking {
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        coEvery { documentSyncService.syncDocuments(any(), workspaceId) } returns null
        val (folderSync, documentsApi) = successfulSync(folderRepository, documentSyncService = documentSyncService)

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify(exactly = 0) { documentsApi.sendFolders(any(), any()) }
        coVerify(exactly = 0) { documentSyncService.receiveDeletedSteps(any(), any()) }
        coVerify(exactly = 0) { documentSyncService.mergeReceivedDocuments(any(), any()) }
        coVerify(exactly = 0) { folderRepository.setContentSyncedAt(any(), any(), any()) }
    }

    @Test
    fun `nothing is sent or received while the deletions can't be sent`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        coEvery { documentSyncService.sendDeletedSteps(workspaceId) } returns null
        coEvery { documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId) } returns listOf(document("doc1", "Edited"))
        val (folderSync, documentsApi) = successfulSync(
            folderRepository,
            documentRepository = documentRepository,
            documentSyncService = documentSyncService
        )

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify(exactly = 0) { documentSyncService.syncDocuments(any(), any()) }
        coVerify(exactly = 0) { documentsApi.sendFolders(any(), any()) }
        coVerify(exactly = 0) { documentSyncService.mergeReceivedDocuments(any(), any()) }
        coVerify(exactly = 0) { folderRepository.setContentSyncedAt(any(), any(), any()) }
    }

    @Test
    fun `the deletions received are applied before the documents, from the folder watermark on`() = runBlocking {
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val contentSyncedAt = Instant.fromEpochMilliseconds(1_000_000)
        coEvery { folderRepository.getContentSyncedAt(folderId, workspaceId) } returns contentSyncedAt
        val (folderSync, _) = successfulSync(folderRepository, documentSyncService = documentSyncService)

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerifyOrder {
            documentSyncService.receiveDeletedSteps(workspaceId, contentSyncedAt)
            documentSyncService.mergeReceivedDocuments(any(), any())
        }
    }

    @Test
    fun `the steps deleted in this sync, here or elsewhere, are kept out of the received documents`() = runBlocking {
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        coEvery { documentSyncService.sendDeletedSteps(workspaceId) } returns setOf("deleted-here")
        coEvery { documentSyncService.receiveDeletedSteps(workspaceId, any()) } returns setOf("deleted-elsewhere")
        val (folderSync, _) = successfulSync(folderRepository, documentSyncService = documentSyncService)

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify { documentSyncService.mergeReceivedDocuments(any(), setOf("deleted-here", "deleted-elsewhere")) }
    }

    @Test
    fun `the watermark doesn't move while the deletions can't be received`() = runBlocking {
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        coEvery { documentSyncService.receiveDeletedSteps(workspaceId, any()) } returns null
        val (folderSync, _) = successfulSync(folderRepository, documentSyncService = documentSyncService)

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify(exactly = 0) { documentSyncService.mergeReceivedDocuments(any(), any()) }
        coVerify(exactly = 0) { folderRepository.setContentSyncedAt(any(), any(), any()) }
    }

    @Test
    fun `syncFolder asks for everything in a folder whose contents were never synced`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        // Received inside its parent: the folder itself is in sync, its contents are not.
        coEvery { folderRepository.getFolderById(folderId) } returns
            Folder.fromName("Received", workspaceId).copy(id = folderId, lastSyncedAt = Clock.System.now())
        coEvery { folderRepository.getContentSyncedAt(folderId, workspaceId) } returns null
        val (folderSync, documentsApi) = successfulSync(folderRepository)

        val before = Clock.System.now()
        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify { documentsApi.getFolderNewData(folderId, workspaceId, Instant.DISTANT_PAST, any()) }
        val saved = slot<Instant>()
        coVerify { folderRepository.setContentSyncedAt(folderId, workspaceId, capture(saved)) }
        assertTrue(saved.captured >= before)
    }

    @Test
    fun `syncFolder asks only for what changed since the folder contents were synced`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val contentSyncedAt = Instant.fromEpochMilliseconds(1_000_000)
        coEvery { folderRepository.getContentSyncedAt(folderId, workspaceId) } returns contentSyncedAt
        val documentsApi = mockk<DocumentsApi>()
        val (folderSync, _) = successfulSync(folderRepository, documentsApi)
        coEvery {
            documentsApi.getFolderNewData(folderId, workspaceId, any(), any())
        } returns ResultData.Error()

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify { documentsApi.getFolderNewData(folderId, workspaceId, contentSyncedAt, any()) }
        // The request failed, so the contents watermark must not move.
        coVerify(exactly = 0) { folderRepository.setContentSyncedAt(any(), any(), any()) }
    }

    @Test
    fun `the root keeps a contents watermark like any other folder`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val rootSyncedAt = Instant.fromEpochMilliseconds(2_000_000)
        // The root has no folder row.
        coEvery { folderRepository.getFolderById(Folder.ROOT_PATH) } returns null
        coEvery { folderRepository.getContentSyncedAt(Folder.ROOT_PATH, workspaceId) } returns rootSyncedAt
        val (folderSync, documentsApi) = successfulSync(folderRepository, syncedFolderId = Folder.ROOT_PATH)

        val before = Clock.System.now()
        folderSync.syncFolder(Folder.ROOT_PATH, workspaceId, force = true)

        coVerify { documentsApi.getFolderNewData(Folder.ROOT_PATH, workspaceId, rootSyncedAt, any()) }
        val saved = slot<Instant>()
        coVerify { folderRepository.setContentSyncedAt(Folder.ROOT_PATH, workspaceId, capture(saved)) }
        assertTrue(saved.captured >= before)
        // No root folder row is ever created.
        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
        coVerify(exactly = 0) { folderRepository.updateFolder(any()) }
    }

    @Test
    fun `syncFolder should not update timestamps when sendFolders fails`() = runBlocking {
        val documentSyncService = syncService()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val (folderSync, _) = successfulSync(folderRepository, documentsApi, documentSyncService = documentSyncService)
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Error()

        folderSync.syncFolder(folderId, workspaceId, force = true)

        coVerify(exactly = 0) { folderRepository.updateFolder(match { it.id == folderId }) }
        coVerify(exactly = 0) { folderRepository.setContentSyncedAt(any(), any(), any()) }
        // Nothing received is applied either: the sync is tried again whole.
        coVerify(exactly = 0) { documentSyncService.receiveDeletedSteps(any(), any()) }
        coVerify(exactly = 0) { documentSyncService.mergeReceivedDocuments(any(), any()) }
    }

    private fun document(
        id: String,
        title: String,
        parentId: String = folderId,
        lastUpdatedAt: Instant = Instant.DISTANT_PAST,
    ) = Document(
        id = id,
        title = title,
        createdAt = Instant.DISTANT_PAST,
        lastUpdatedAt = lastUpdatedAt,
        lastSyncedAt = null,
        workspaceId = workspaceId,
        parentId = parentId
    )

    /** A step sync that succeeds at everything and receives nothing. */
    private fun syncService(): DocumentSyncService = mockk<DocumentSyncService>(relaxed = true).also { service ->
        coEvery { service.sendDeletedSteps(any()) } returns emptySet()
        coEvery { service.syncDocuments(any(), any()) } returns emptyList()
        coEvery { service.receiveDeletedSteps(any(), any()) } returns emptySet()
    }

    private fun folderSync(
        documentRepository: DocumentRepository = mockk(relaxed = true),
        documentsApi: DocumentsApi,
        documentSyncService: DocumentSyncService = syncService(),
        folderConflictHandler: FolderConflictHandler = mockk<FolderConflictHandler>().also {
            coEvery { it.handleConflict(any(), any()) } returns emptyList()
        },
        folderRepository: FolderRepository = mockk(relaxed = true),
        documentTagRepository: InMemoryDocumentTagRepository? = null,
        documentTagConflictHandler: DocumentTagConflictHandler? = null,
    ): FolderSync = FolderSync(
        documentRepository = documentRepository,
        documentsApi = documentsApi,
        documentSyncService = documentSyncService,
        folderConflictHandler = folderConflictHandler,
        folderRepository = folderRepository,
        authRepository = mockk<AuthRepository>(),
        minSyncInternal = 0.milliseconds,
        documentTagRepository = documentTagRepository,
        documentTagConflictHandler = documentTagConflictHandler,
    )

    private fun successfulSync(
        folderRepository: FolderRepository,
        documentsApi: DocumentsApi = mockk(),
        documentRepository: DocumentRepository = mockk(relaxed = true),
        documentSyncService: DocumentSyncService = syncService(),
        syncedFolderId: String = folderId,
    ): Pair<FolderSync, DocumentsApi> {
        coEvery {
            documentsApi.getFolderNewData(syncedFolderId, workspaceId, any(), any())
        } returns ResultData.Complete(FolderContentResponse())
        coEvery { folderRepository.localOutDatedFolders(workspaceId) } returns emptyList()
        coEvery { documentsApi.sendFolders(any(), workspaceId) } returns ResultData.Complete(Unit)

        val folderSync = folderSync(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentSyncService = documentSyncService,
            folderRepository = folderRepository,
        )
        return folderSync to documentsApi
    }
}
