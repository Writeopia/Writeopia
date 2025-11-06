package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FolderSync(
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentConflictHandler: DocumentConflictHandler,
    private val folderRepository: FolderRepository,
    private val authRepository: AuthRepository
) {
    /**
     * Sync the folder with the backend end. The lastSync should be data fetched from the backend.
     *
     * This logic is atomic. If it fails, the whole process must be tried again in a future time.
     * The sync time of the folder will only be updated with everything works correctly.
     */
    suspend fun syncFolder(folderId: String, workspaceId: String) {
        val authToken = authRepository.getAuthToken() ?: return

//        println("folderId: $folderId")
        val folder: Folder = folderRepository.getFolderById(folderId) ?: run {
            val folder = Folder(
                id = "root",
                parentId = "null",
                title = "root",
                createdAt = Instant.DISTANT_PAST,
                lastUpdatedAt = Instant.DISTANT_PAST,
                itemCount = 0,
                workspaceId = workspaceId,
            )

            folderRepository.createFolder(folder)
            folder
        }

        val lastSync = folder.lastSyncedAt
//        println("Sync. lastSync: $lastSync")

        // First, receive the documents for the backend.
        val response = documentsApi.getFolderNewDocuments(
            folderId,
            workspaceId,
            lastSync ?: Instant.DISTANT_PAST,
            authToken
        )
        val newDocuments = if (response is ResultData.Complete) response.data else return
//        println("Sync. received ${newDocuments.size} new documents")
//        println("Documents: ${newDocuments.joinToString(separator = "\n\n")}")

        // Then, load the outdated documents.
        // These documents were updated locally, but were not sent to the backend yet
        val localOutdatedDocs = documentRepository.loadOutdatedDocuments(folderId, workspaceId)

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
            val now = Clock.System.now()
            // If everything ran accordingly, update the sync time of the folder.
            documentsNotSent.forEach { document ->
                val newDocument = document.copy(lastSyncedAt = now)
                documentRepository.saveDocumentMetadata(newDocument)
            }

            documentRepository.refreshDocuments()
            folderRepository.updateFolder(folder.copy(lastSyncedAt = now))
        }
    }
}
