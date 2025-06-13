package io.writeopia.core.folders.sync

import io.writeopia.common.utils.ResultData
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.FolderRepository
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class DocumentsSync(
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentConflictHandler: DocumentConflictHandler,
    private val folderRepository: FolderRepository
) {
    /**
     * Sync the folder with the backend end. The lastSync should be data fetched from the backend.
     *
     * This logic is atomic. If it fails, the whole process must be tried again in a future time.
     * The sync time of the folder will only be updated with everything works correctly.
     */
    suspend fun syncFolder(folderId: String, userId: String) {
//        println("folderId: $folderId")
        val folder: Folder = folderRepository.getFolderById(folderId) ?: run {
            val folder = Folder(
                id = "root",
                parentId = "null",
                title = "root",
                createdAt = Instant.DISTANT_PAST,
                lastUpdatedAt = Instant.DISTANT_PAST,
                itemCount = 0,
                userId = userId,
            )

            folderRepository.createFolder(folder)
            folder
        }

        val lastSync = folder.lastSyncedAt
//        println("Sync. lastSync: $lastSync")

        // First, receive the documents for the backend.
        val response = documentsApi.getNewDocuments(
            folderId,
            lastSync ?: Instant.DISTANT_PAST
        )
        val newDocuments = if (response is ResultData.Complete) response.data else return
//        println("Sync. received ${newDocuments.size} new documents")
//        println("Documents: ${newDocuments.joinToString(separator = "\n\n")}")

        // Then, load the outdated documents.
        // These documents were updated locally, but were not sent to the backend yet
        val localOutdatedDocs = documentRepository.loadOutdatedDocuments(folderId)

        // Resolve conflicts of documents that were updated both locally and in the backend.
        // Documents will be saved locally by documentConflictHandler.handleConflict
        val documentsNotSent =
            documentConflictHandler.handleConflict(localOutdatedDocs, newDocuments)
        documentRepository.refreshDocuments()

//        println("Sync. sending ${documentsNotSent.size} documents")
//        println("Documents sent: ${documentsNotSent.joinToString(separator = "\n\n")}")

        // Send documents to backend
        val resultSend = documentsApi.sendDocuments(documentsNotSent)

        if (resultSend is ResultData.Complete) {
            val now = Clock.System.now()
            // If everything ran accordingly, update the sync time of the folder.
            documentsNotSent.forEach { document ->
                val newDocument = document.copy(lastSyncedAt = now)
                documentRepository.saveDocumentMetadata(newDocument, userId)
            }

            documentRepository.refreshDocuments()
            folderRepository.updateFolder(folder.copy(lastSyncedAt = now))
        } else {
            return
        }
    }
}
