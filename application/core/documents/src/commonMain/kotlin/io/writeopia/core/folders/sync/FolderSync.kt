@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.extensions.toModel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class FolderSync(
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentConflictHandler: DocumentConflictHandler,
    private val folderConflictHandler: FolderConflictHandler,
    private val folderRepository: FolderRepository,
    private val authRepository: AuthRepository,
    private val minSyncInternal: Duration = 2.seconds
) {

    private var lastSuccessfulSync: Instant = Instant.DISTANT_PAST

    /**
     * Sync the folder with the backend end. The lastSync should be data fetched from the backend.
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
        try {
            if (workspaceId == Workspace.disconnectedWorkspace().id) {
                syncLog("Skipping sync of folder $folderId: disconnected workspace")
                return
            }

            val now = Clock.System.now()
            if (!force && now - lastSuccessfulSync < minSyncInternal) {
                syncLog(
                    "Skipping sync of folder $folderId: last successful sync was at $lastSuccessfulSync " +
                        "(min interval: $minSyncInternal)"
                )
                return
            }

            syncLog("===== Sync start - folderId: $folderId, workspaceId: $workspaceId, force: $force, orderBy: $orderBy")

            val existingFolder = folderRepository.getFolderById(folderId)
            syncLog("Local folder being synced: ${existingFolder?.syncDescription()}")

            // Use the existing folder's lastSyncedAt, or DISTANT_PAST if folder doesn't exist
            // We don't create a fallback folder to avoid creating unwanted "root" folders
            val lastSync = existingFolder?.lastSyncedAt

            // First, receive the documents and subfolders from the backend.
            val response = documentsApi.getFolderNewData(
                folderId,
                workspaceId,
                lastSync ?: Instant.DISTANT_PAST,
                orderBy
            )

            syncLog("Requested backend diff with lastSync: ${lastSync ?: Instant.DISTANT_PAST}")

            val folderContent = if (response is ResultData.Complete) {
                response.data
            } else {
                syncLog("Aborting sync: backend request failed. Response: $response")
                return
            }

            val newDocuments = folderContent.documents.map { it.toModel() }
            val newFolders = folderContent.folders.map { it.toModel() }

            logDocuments("Documents received from backend", newDocuments)
            logFolders("Folders received from backend", newFolders)

            // Then, load the outdated documents.
            // These documents were updated locally, but were not sent to the backend yet
            val localOutdatedDocs = documentRepository.loadOutdatedDocumentsByFolder(folderId, workspaceId)

            // Load local outdated subfolders (where lastSyncedAt is null or lastUpdatedAt > lastSyncedAt)
            val allLocalFolders = folderRepository.getFolderByParentId(folderId, workspaceId)
            val localOutdatedFolders = allLocalFolders.filter { folder ->
                val syncedAt = folder.lastSyncedAt
                syncedAt == null || folder.lastUpdatedAt > syncedAt
            }

            logDocuments("Local outdated documents (not sent yet)", localOutdatedDocs)
            logFolders("All local subfolders", allLocalFolders)
            logFolders("Local outdated subfolders (not sent yet)", localOutdatedFolders)

            // Resolve conflicts of documents that were updated both locally and in the backend.
            // Documents will be saved locally by documentConflictHandler.handleConflict
            val documentsNotSent =
                documentConflictHandler.handleConflict(localOutdatedDocs, newDocuments)

            // Resolve conflicts for subfolders
            val foldersNotSent = folderConflictHandler.handleConflict(
                localFolders = localOutdatedFolders,
                externalFolders = newFolders
            )

            documentRepository.refreshDocuments()
            folderRepository.refreshFolders()

            logDocuments("Documents to send to backend", documentsNotSent)
            logFolders("Folders to send to backend", foldersNotSent)

            // Send documents to backend
            val resultSendDocuments = documentsApi.sendDocuments(documentsNotSent, workspaceId)
            syncLog("Send documents result: $resultSendDocuments")

            // Send subfolders to backend
            val resultSendFolders = documentsApi.sendFolders(foldersNotSent, workspaceId)
            syncLog("Send folders result: $resultSendFolders")

            if (resultSendDocuments is ResultData.Complete && resultSendFolders is ResultData.Complete) {
                // Documents and folders were sent successfully.
                // Update lastSyncedAt for sent items to prevent re-sending them.
                val syncTime = Clock.System.now()

                // Update lastSyncedAt for documents that were sent
                documentsNotSent.forEach { doc ->
                    val updatedDoc = doc.copy(lastSyncedAt = syncTime)
                    documentRepository.saveDocument(updatedDoc)
                }

                // Update lastSyncedAt for folders that were sent
                foldersNotSent.forEach { folder ->
                    val updatedFolder = folder.copy(lastSyncedAt = syncTime)
                    folderRepository.updateFolder(updatedFolder)
                }

                documentRepository.refreshDocuments()
                folderRepository.refreshFolders()

                lastSuccessfulSync = syncTime

                syncLog(
                    "===== Sync success - folderId: $folderId. Marked ${documentsNotSent.size} documents and " +
                        "${foldersNotSent.size} folders with lastSyncedAt: $syncTime. " +
                        "Note: lastSyncedAt of folder $folderId itself was not updated " +
                        "(still ${existingFolder?.lastSyncedAt})"
                )
            } else {
                syncLog("===== Sync incomplete - folderId: $folderId. lastSyncedAt was not updated")
            }
        } catch (e: Exception) {
            // Sync failed, will retry on next sync
            syncLog("===== Sync failed with exception - folderId: $folderId: ${e.message}")
            e.printStackTrace()
        }
    }
}
