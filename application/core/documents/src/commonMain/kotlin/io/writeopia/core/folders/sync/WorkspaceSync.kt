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
                println("Skipping sync for $workspaceId. Last sync was less than $minSyncInternal ago.")
                return ResultData.Idle()
            }

            println("start to sync workspace")
            val authToken = authRepository.getAuthToken() ?: return ResultData.Error(null)
            val workspace = authRepository.getWorkspace() ?: return ResultData.Idle()

            val response = documentsApi.getWorkspaceNewData(
                workspaceId,
                workspace.lastSync,
                authToken
            )
            val (newDocuments, newFolders) = if (response is ResultData.Complete) {
                println("Received response from API")
                response.data
            } else {
                println("Error syncing workspace")
                return ResultData.Error()
            }

            val localOutdatedDocs =
                documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId)
            val localOutdatedFolders = folderRepository.localOutDatedFolders(workspaceId)

            println("local outdated folders: ${localOutdatedFolders.size}")
            println("local outdated docs: ${localOutdatedDocs.size}")

            val documentsNotSent = documentConflictHandler.handleConflict(
                localOutdatedDocs,
                newDocuments,
            )

            val foldersNotSent = documentConflictHandler.handleConflictForFolders(
                localFolders = localOutdatedFolders,
                externalFolders = newFolders,
            )

            println("sending ${documentsNotSent.size} documents")
            val resultSendDocuments =
                documentsApi.sendDocuments(documentsNotSent, workspaceId, authToken)

            println("sending ${foldersNotSent.size} folders")
            val resultSendFolders = documentsApi.sendFolders(foldersNotSent, workspaceId, authToken)

            if (
                resultSendDocuments is ResultData.Complete &&
                resultSendFolders is ResultData.Complete
            ) {
                println("documents sent")
                val now = Clock.System.now()
                // If everything ran accordingly, update the sync time of the folder.
                documentsNotSent.forEach { document ->
                    val newDocument = document.copy(lastSyncedAt = now)
                    documentRepository.saveDocumentMetadata(newDocument)
                }

                documentRepository.refreshDocuments()
                folderRepository.refreshFolders()

                lastSuccessfulSync = now

                return ResultData.Complete(Unit)
            } else {
                println("documents NOT sent")
                return ResultData.Error()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ResultData.Error(e)
        }
    }
}
