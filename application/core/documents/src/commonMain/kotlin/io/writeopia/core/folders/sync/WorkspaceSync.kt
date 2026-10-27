@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Syncs the whole workspace, the way [FolderSync] syncs a folder: documents go and come one
 * step at a time through [DocumentSyncService], never replaced whole.
 *
 * Receiving covers every folder at once, so what changed elsewhere arrives wherever it is: a
 * document favorited on another device shows up in the favorites even when its folder was
 * never opened here. The workspace keeps one contents watermark ([WATERMARK_ID]), like a folder
 * keeps its own, so only what changed since the last sync is asked for.
 */
class WorkspaceSync(
    private val folderRepository: FolderRepository,
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentSyncService: DocumentSyncService,
    private val folderConflictHandler: FolderConflictHandler,
    private val imageSync: ImageSync,
    private val documentTagRepository: DocumentTagRepository? = null,
    private val documentTagConflictHandler: DocumentTagConflictHandler? = null,
    private val minSyncInternal: Duration = 3.seconds
) {
    /**
     * Syncs [workspaceId] with the backend. One sync runs at a time, whoever asks: a second
     * caller (the documents screen opening while the login sync still runs) waits for the
     * first, then receives only what changed since, or nothing when [force] is false and the
     * first just ended.
     */
    suspend fun syncWorkspace(workspaceId: String, force: Boolean = false): ResultData<Unit> =
        SyncActivity.track {
            running.withLock { syncWorkspaceInternal(workspaceId, force) }
        }

    private suspend fun syncWorkspaceInternal(
        workspaceId: String,
        force: Boolean
    ): ResultData<Unit> {
        try {
            if (workspaceId == Workspace.disconnectedWorkspace().id) {
                return ResultData.Complete(Unit)
            }

            val now = Clock.System.now()
            val lastSuccessfulSync = lastSuccessfulSyncs[workspaceId] ?: Instant.DISTANT_PAST
            if (!force && now - lastSuccessfulSync < minSyncInternal) {
                return ResultData.Idle()
            }

            // The whole workspace was never received when there is no watermark yet.
            val lastSync = folderRepository.getContentSyncedAt(WATERMARK_ID, workspaceId)

            val response = documentsApi.getWorkspaceNewData(
                workspaceId,
                lastSync ?: Instant.DISTANT_PAST
            )
            val (newDocuments, newFolders, newTags) = if (response is ResultData.Complete) {
                response.data
            } else {
                return ResultData.Error()
            }

            // Deletions first, then the edited steps and the metadata of the outdated documents.
            val deletedHere = documentSyncService.sendDeletedSteps(workspaceId) ?: return ResultData.Error()

            val localOutdatedDocs = documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId)
            documentSyncService.syncDocuments(localOutdatedDocs, workspaceId) ?: return ResultData.Error()

            val localOutdatedFolders = folderRepository.localOutDatedFolders(workspaceId)
            val foldersNotSent = folderConflictHandler.handleConflict(
                localFolders = localOutdatedFolders,
                externalFolders = newFolders,
            )

            val resultSendFolders = documentsApi.sendFolders(foldersNotSent, workspaceId)

            val tagsNotSent = documentTagConflictHandler?.handleConflict(
                localTags = documentTagRepository?.localOutdatedTags(workspaceId) ?: emptyList(),
                externalTags = newTags,
            ) ?: emptyList()
            val resultSendTags =
                if (tagsNotSent.isEmpty()) ResultData.Complete(Unit) else documentsApi.sendTags(tagsNotSent, workspaceId)

            if (resultSendFolders is ResultData.Complete && resultSendTags is ResultData.Complete) {
                val syncTime = Clock.System.now()

                // Update lastSyncedAt for folders that were sent
                foldersNotSent.forEach { folder ->
                    val updatedFolder = folder.copy(lastSyncedAt = syncTime)
                    folderRepository.updateFolder(updatedFolder)
                }

                tagsNotSent.forEach { tag ->
                    documentTagRepository?.upsertTag(tag.copy(lastSyncedAt = syncTime))
                }

                // What changed elsewhere: deletions first, then the documents, step by step.
                val deletionsSince = folderRepository.getContentSyncedAt(DocumentSyncService.DELETIONS_WATERMARK_ID, workspaceId)
                val received = documentSyncService.receiveDeletedSteps(workspaceId, deletionsSince ?: Instant.DISTANT_PAST)
                    ?: return ResultData.Error()
                documentSyncService.mergeReceivedDocuments(newDocuments, excluding = deletedHere + received.stepIds)
                folderRepository.setContentSyncedAt(DocumentSyncService.DELETIONS_WATERMARK_ID, workspaceId, received.serverTimestamp)
                // The favorites of the documents don't travel with them: they come apart. The
                // whole list comes each time, so when it can't be asked the sync still ends
                // here and the next one brings it; the content merged above stays synced.
                documentSyncService.receiveFavorites(workspaceId)

                // The whole workspace is now on this device. The watermark is the time taken
                // before the request, so whatever changed while it ran comes again next time.
                folderRepository.setContentSyncedAt(WATERMARK_ID, workspaceId, now)

                documentRepository.refreshDocuments()
                folderRepository.refreshFolders()

                lastSuccessfulSyncs[workspaceId] = syncTime

                imageSync.syncAllImages(workspaceId = workspaceId)

                return ResultData.Complete(Unit)
            } else {
                return ResultData.Error()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return ResultData.Error(e)
        }
    }

    companion object {
        /**
         * The contents watermark of the whole workspace, kept like a folder's: the key can't be
         * a folder id.
         */
        const val WATERMARK_ID = "__workspace__"

        /** Held by the sync in progress: two at once would merge the same documents twice. */
        private val running = Mutex()

        /** By workspace, shared by every instance, so the throttle counts whoever synced last. */
        private val lastSuccessfulSyncs = mutableMapOf<String, Instant>()
    }
}
