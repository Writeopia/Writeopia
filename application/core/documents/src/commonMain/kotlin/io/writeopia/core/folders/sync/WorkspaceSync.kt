@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class WorkspaceSync(
    private val folderRepository: FolderRepository,
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi,
    private val documentConflictHandler: DocumentConflictHandler,
    private val folderConflictHandler: FolderConflictHandler,
    private val imageSync: ImageSync,
    private val minSyncInternal: Duration = 3.seconds
) {
    private var lastSuccessfulSync: Instant = Instant.DISTANT_PAST

    suspend fun syncWorkspace(workspaceId: String, force: Boolean = false): ResultData<Unit> {
        try {
            if (workspaceId == Workspace.disconnectedWorkspace().id) {
                return ResultData.Complete(Unit)
            }

            val now = Clock.System.now()
            if (!force && now - lastSuccessfulSync < minSyncInternal) {
                return ResultData.Idle()
            }

            val authToken = authRepository.getAuthToken() ?: return ResultData.Error(null)
            val workspace = authRepository.getWorkspace() ?: return ResultData.Idle()

            val response = documentsApi.getWorkspaceNewData(
                workspaceId,
                workspace.lastSync,
                authToken
            )
            val (newDocuments, newFolders) = if (response is ResultData.Complete) {
                response.data
            } else {
                return ResultData.Error()
            }

            val localOutdatedDocs =
                documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId)
            val localOutdatedFolders = folderRepository.localOutDatedFolders(workspaceId)

            val documentsNotSent = documentConflictHandler.handleConflict(
                localOutdatedDocs,
                newDocuments,
            )

            val foldersNotSent = folderConflictHandler.handleConflict(
                localFolders = localOutdatedFolders,
                externalFolders = newFolders,
            )

            val resultSendDocuments =
                documentsApi.sendDocuments(documentsNotSent, workspaceId, authToken)

            val resultSendFolders = documentsApi.sendFolders(foldersNotSent, workspaceId, authToken)

            if (
                resultSendDocuments is ResultData.Complete &&
                resultSendFolders is ResultData.Complete
            ) {
                val now = Clock.System.now()
                // If everything ran accordingly, update the sync time of the folder.
                documentsNotSent.forEach { document ->
                    val newDocument = document.copy(lastSyncedAt = now)
                    documentRepository.saveDocumentMetadata(newDocument)
                }

                documentRepository.refreshDocuments()
                folderRepository.refreshFolders()

                lastSuccessfulSync = now

                imageSync.syncAllImages(workspaceId = workspaceId, token = authToken)

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
