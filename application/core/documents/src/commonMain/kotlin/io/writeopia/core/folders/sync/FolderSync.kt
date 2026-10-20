@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.extensions.toModel
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class FolderSync(
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentSyncService: DocumentSyncService,
    private val folderConflictHandler: FolderConflictHandler,
    private val folderRepository: FolderRepository,
    private val authRepository: AuthRepository,
    private val minSyncInternal: Duration = 2.seconds,
    private val documentTagRepository: DocumentTagRepository? = null,
    private val documentTagConflictHandler: DocumentTagConflictHandler? = null,
) {

    private var lastSuccessfulSync: Instant = Instant.DISTANT_PAST

    /**
     * Sync the folder with the backend end. The lastSync should be data fetched from the backend.
     *
     * Receiving is about this folder only. Sending covers the whole workspace: every document
     * and folder changed locally since it was last sent goes with this sync, wherever it is, so
     * a change (a move, for one) doesn't wait for its own folder to be opened.
     *
     * Documents travel one step at a time, through [DocumentSyncService]: first the deletions
     * made here, then the steps edited here, each kept by the backend only when newer than its
     * own copy. What comes back (the steps deleted and changed elsewhere) is merged into the
     * local copy the same way. A document is never replaced whole on either side.
     *
     * This logic is atomic. If it fails, the whole process must be tried again in a future time.
     * The sync time of the folder will only be updated with everything works correctly.
     */
    suspend fun syncFolder(
        folderId: String,
        workspaceId: String,
        force: Boolean = false,
        orderBy: String = "last_updated_at"
    ) {
        SyncActivity.track { syncFolderInternal(folderId, workspaceId, force, orderBy) }
    }

    private suspend fun syncFolderInternal(
        folderId: String,
        workspaceId: String,
        force: Boolean,
        orderBy: String
    ) {
        try {
            if (workspaceId == Workspace.disconnectedWorkspace().id) return

            val now = Clock.System.now()
            if (!force && now - lastSuccessfulSync < minSyncInternal) return

            // The contents watermark, not the folder's lastSyncedAt: a folder received inside its
            // parent is stamped as synced, but its documents were never fetched. The root keeps
            // its watermark the same way, without needing a folder row.
            val lastSync = folderRepository.getContentSyncedAt(folderId, workspaceId)

            // First, receive the documents and subfolders from the backend.
            val response = documentsApi.getFolderNewData(
                folderId,
                workspaceId,
                lastSync ?: Instant.DISTANT_PAST,
                orderBy
            )

            val folderContent = if (response is ResultData.Complete) {
                response.data
            } else {
                return
            }

            val newDocuments = folderContent.documents.map { it.toModel() }
            val newFolders = folderContent.folders.map { it.toModel() }
            // The whole tag catalogue of the workspace comes with every folder.
            val newTags = folderContent.tags.map { it.toModel() }

            // Send what was deleted here before anything else: a step the backend still has is
            // deleted there instead of coming back.
            val deletedHere = documentSyncService.sendDeletedSteps(workspaceId) ?: return

            // Then the documents of the whole workspace changed locally since they were sent:
            // their edited steps, then their metadata (title, folder, icon...), stamped as synced
            // only once both are on the backend.
            val localOutdatedDocs = documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId)
            documentSyncService.syncDocuments(localOutdatedDocs, workspaceId) ?: return

            // Resolve conflicts for subfolders
            val localOutdatedFolders = folderRepository.localOutDatedFolders(workspaceId)
            val foldersNotSent = folderConflictHandler.handleConflict(
                localFolders = localOutdatedFolders,
                externalFolders = newFolders
            )
            folderRepository.refreshFolders()

            // Send subfolders to backend
            val resultSendFolders = documentsApi.sendFolders(foldersNotSent, workspaceId)

            val tagsNotSent = documentTagConflictHandler?.handleConflict(
                localTags = documentTagRepository?.localOutdatedTags(workspaceId) ?: emptyList(),
                externalTags = newTags,
            ) ?: emptyList()
            val resultSendTags =
                if (tagsNotSent.isEmpty()) ResultData.Complete(Unit) else documentsApi.sendTags(tagsNotSent, workspaceId)

            if (resultSendFolders is ResultData.Complete && resultSendTags is ResultData.Complete) {
                // Folders and tags were sent successfully.
                // Update lastSyncedAt for sent items to prevent re-sending them.
                val syncTime = Clock.System.now()

                // Update lastSyncedAt for folders that were sent
                foldersNotSent.forEach { folder ->
                    val updatedFolder = folder.copy(lastSyncedAt = syncTime)
                    folderRepository.updateFolder(updatedFolder)
                }

                tagsNotSent.forEach { tag ->
                    documentTagRepository?.upsertTag(tag.copy(lastSyncedAt = syncTime))
                }

                // Now what changed elsewhere: the steps deleted since the last sync go first, so
                // nothing received recreates them; then the documents, merged step by step. The
                // listing predates the deletions sent above, so it may still carry those steps.
                val deletionsSince = folderRepository.getContentSyncedAt(DocumentSyncService.DELETIONS_WATERMARK_ID, workspaceId)
                val received =
                    documentSyncService.receiveDeletedSteps(workspaceId, deletionsSince ?: Instant.DISTANT_PAST) ?: return
                documentSyncService.mergeReceivedDocuments(newDocuments, excluding = deletedHere + received.stepIds)
                // Workspace-wide, in the backend's clock.
                folderRepository.setContentSyncedAt(DocumentSyncService.DELETIONS_WATERMARK_ID, workspaceId, received.serverTimestamp)
                // The favorites of the documents don't travel with them: they come apart. The
                // whole list comes each time, so when it can't be asked the sync still ends
                // here and the next one brings it; the content merged above stays synced.
                documentSyncService.receiveFavorites(workspaceId)

                // The contents of this folder are now on this device. The watermark is the time
                // taken before the request, so whatever changed while it ran comes again next time.
                folderRepository.setContentSyncedAt(folderId, workspaceId, now)

                documentRepository.refreshDocuments()
                folderRepository.refreshFolders()

                lastSuccessfulSync = syncTime
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sync failed, will retry on next sync. Said out loud: a silent failure here hides
            // a device that never syncs.
            println("Error syncing folder $folderId: ${e.message}")
        }
    }
}
