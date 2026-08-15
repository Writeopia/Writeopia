@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class FolderSync(
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentConflictHandler: DocumentConflictHandler,
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
            if (!force && now - lastSuccessfulSync < minSyncInternal) {
                println("Skipping sync for $workspaceId. Last sync was less than $minSyncInternal ago.")
            }

            val authToken = authRepository.getAuthToken() ?: return

            println("syncFolder folderId: $folderId")
            val existingFolder = folderRepository.getFolderById(folderId)
            println("syncFolder existingFolder: $existingFolder")

            // Use the existing folder's lastSyncedAt, or DISTANT_PAST if folder doesn't exist
            // We don't create a fallback folder to avoid creating unwanted "root" folders
            val lastSync = existingFolder?.lastSyncedAt
            println("Sync. lastSync: $lastSync")

            // First, receive the documents for the backend.
            val response = documentsApi.getFolderNewDocuments(
                folderId,
                workspaceId,
                lastSync ?: Instant.DISTANT_PAST,
                authToken,
                orderBy
            )

            val newDocuments = if (response is ResultData.Complete) {
                response.data
            } else {
                println("newDocuments failed.")
                return
            }
            println("Sync. received ${newDocuments.size} new documents")
//        println("Documents: ${newDocuments.joinToString(separator = "\n\n")}")

            // Then, load the outdated documents.
            // These documents were updated locally, but were not sent to the backend yet
            val localOutdatedDocs = documentRepository.loadOutdatedDocumentsByFolder(folderId, workspaceId)
            println("Sync. loaded ${localOutdatedDocs.size} outdated documents")

            // Resolve conflicts of documents that were updated both locally and in the backend.
            // Documents will be saved locally by documentConflictHandler.handleConflict
            val documentsNotSent =
                documentConflictHandler.handleConflict(localOutdatedDocs, newDocuments)
            documentRepository.refreshDocuments()

//        println("Sync. sending ${documentsNotSent.size} documents")
//        println("Documents sent: ${documentsNotSent.joinToString(separator = "\n\n")}")

            // Send documents to backend
            val resultSend = documentsApi.sendDocuments(documentsNotSent, workspaceId, authToken)

            if (resultSend is ResultData.Complete) {
                println("documents sent")
                val now = Clock.System.now()
                // If everything ran accordingly, update the sync time of the folder.
                documentsNotSent.forEach { document ->
                    val newDocument = document.copy(lastSyncedAt = now)
                    documentRepository.saveDocumentMetadata(newDocument)
                }

                documentRepository.refreshDocuments()

                // Only update folder sync time if folder exists locally
                existingFolder?.let { folder ->
                    folderRepository.updateFolder(folder.copy(lastSyncedAt = now))
                }

                println("folders sync OK")

                lastSuccessfulSync = now
            }
        } catch (e: Exception) {
//            e.printStackTrace()
            println("Error in folder sync: ${e.message}")
        }
    }
}
