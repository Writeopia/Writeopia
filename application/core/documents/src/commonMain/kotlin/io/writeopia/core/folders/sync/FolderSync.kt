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
            if (workspaceId == Workspace.disconnectedWorkspace().id) return

            val now = Clock.System.now()
            if (!force && now - lastSuccessfulSync < minSyncInternal) return

            val existingFolder = folderRepository.getFolderById(folderId)

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

            val folderContent = if (response is ResultData.Complete) {
                response.data
            } else {
                return
            }

            val newDocuments = folderContent.documents.map { it.toModel() }
            val newFolders = folderContent.folders.map { it.toModel() }

            println("[FolderSync] received ${newFolders.size} new folders")

            // Then, load the outdated documents.
            // These documents were updated locally, but were not sent to the backend yet
            val localOutdatedDocs = documentRepository.loadOutdatedDocumentsByFolder(folderId, workspaceId)

            // Load local outdated subfolders (where lastSyncedAt is null or lastUpdatedAt > lastSyncedAt)
            val allLocalFolders = folderRepository.getFolderByParentId(folderId, workspaceId)
            val localOutdatedFolders = allLocalFolders.filter { folder ->
                val syncedAt = folder.lastSyncedAt
                syncedAt == null || folder.lastUpdatedAt > syncedAt
            }

            println("[FolderSync] Found ${localOutdatedFolders.size} outdated folders to sync in folderId=$folderId")
            localOutdatedFolders.forEach { folder ->
                println("[FolderSync] Outdated folder: id=${folder.id}, title=${folder.title}, lastSyncedAt=${folder.lastSyncedAt}, lastUpdatedAt=${folder.lastUpdatedAt}")
            }

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

            // Send documents to backend
            val resultSendDocuments = documentsApi.sendDocuments(documentsNotSent, workspaceId)

            // Send subfolders to backend
            println("[FolderSync] Sending ${foldersNotSent.size} folders to backend")
            val resultSendFolders = documentsApi.sendFolders(foldersNotSent, workspaceId)
            println("[FolderSync] Send folders result: $resultSendFolders")

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
                    println("[FolderSync] Updating lastSyncedAt for folder: id=${folder.id}, title=${folder.title}")
                    val updatedFolder = folder.copy(lastSyncedAt = syncTime)
                    folderRepository.updateFolder(updatedFolder)
                }

                documentRepository.refreshDocuments()
                folderRepository.refreshFolders()

                lastSuccessfulSync = syncTime
            }
        } catch (e: Exception) {
            // Sync failed, will retry on next sync
            println("[FolderSync] Sync failed with exception: ${e.message}")
        }
    }
}
