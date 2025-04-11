package io.writeopia.core.folders.sync

import io.writeopia.common.utils.ResultData
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class DocumentsSync(
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentConflictHandler: DocumentConflictHandler
) {

    /**
     * Sync the folder with the backend end. The lastSync should be data fetched from the backend.
     *
     * This logic is atomic. If it fails, the whole process must be tried again in a future time.
     * The sync time of the folder will only be updated with everything works correctly.
     */
    suspend fun syncFolder(folderId: String) {
        println("Syncing folder")
        println("documentRepository.loadDocumentById")
        val lastSync = documentRepository.loadDocumentById(folderId)?.lastSyncedAt

        // First, receive the documents for the backend.
        println("documentsApi.getNewDocuments")
        val response = documentsApi.getNewDocuments(folderId, lastSync ?: Instant.DISTANT_PAST)
        println("documentsApi.getNewDocuments - results received")
        val newDocuments = if (response is ResultData.Complete) response.data else return
        println("Received ${response.data.size} documents from API")

        // Then, load the outdated documents.
        // These documents were updated locally, but were not sent to the backend yet
        println("documentRepository.loadOutdatedDocuments")
        val localOutdatedDocs = documentRepository.loadOutdatedDocuments(folderId)
        println("localOutdatedDocs.size: ${localOutdatedDocs.size}")

        // Resolve conflicts of documents that were updated both locally and in the backend.
        // Documents will be saved locally by documentConflictHandler.handleConflict
        val documentsNotSent =
            documentConflictHandler.handleConflict(localOutdatedDocs, newDocuments)
        documentRepository.refreshDocuments()

        // Send documents to backend
        println("sending documents")
        val resultSend = documentsApi.sendDocuments(documentsNotSent)

        if (resultSend is ResultData.Complete) {
            println("Updating documents. Size: ${documentsNotSent.size}")
            val now = Clock.System.now()
            // If everything ran accordingly, update the sync time of the folder.
            documentsNotSent.forEach { document ->
                val newDocument = document.copy(lastSyncedAt = now)
                println("saving document. :lastSyncedAt ${newDocument.lastSyncedAt}")
                documentRepository.saveDocumentMetadata(newDocument)
            }

            println("refreshing folders")
            documentRepository.refreshDocuments()
        } else {
            return
        }
    }
}
