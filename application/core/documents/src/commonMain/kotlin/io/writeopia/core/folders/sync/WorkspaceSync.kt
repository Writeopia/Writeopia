@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Syncs the whole workspace, the way [FolderSync] syncs a folder: documents go and come one
 * step at a time through [DocumentSyncService], never replaced whole.
 */
class WorkspaceSync(
    private val folderRepository: FolderRepository,
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi,
    private val documentSyncService: DocumentSyncService,
    private val folderConflictHandler: FolderConflictHandler,
    private val imageSync: ImageSync,
    private val documentTagRepository: DocumentTagRepository? = null,
    private val documentTagConflictHandler: DocumentTagConflictHandler? = null,
    private val minSyncInternal: Duration = 3.seconds
) {
    private var lastSuccessfulSync: Instant = Instant.DISTANT_PAST

    suspend fun syncWorkspace(workspaceId: String, force: Boolean = false): ResultData<Unit> =
        SyncActivity.track { syncWorkspaceInternal(workspaceId, force) }

    private suspend fun syncWorkspaceInternal(
        workspaceId: String,
        force: Boolean
    ): ResultData<Unit> {
        try {
            if (workspaceId == Workspace.disconnectedWorkspace().id) {
                return ResultData.Complete(Unit)
            }

            val now = Clock.System.now()
            if (!force && now - lastSuccessfulSync < minSyncInternal) {
                return ResultData.Idle()
            }

            val workspace = authRepository.getWorkspace() ?: return ResultData.Idle()

            val response = documentsApi.getWorkspaceNewData(
                workspaceId,
                workspace.lastSync
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
                val deletedElsewhere = documentSyncService.receiveDeletedSteps(workspaceId, workspace.lastSync)
                    ?: return ResultData.Error()
                documentSyncService.mergeReceivedDocuments(newDocuments, excluding = deletedHere + deletedElsewhere)

                documentRepository.refreshDocuments()
                folderRepository.refreshFolders()

                lastSuccessfulSync = syncTime

                imageSync.syncAllImages(workspaceId = workspaceId)

                return ResultData.Complete(Unit)
            } else {
                return ResultData.Error()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ResultData.Error(e)
        }
    }
}
