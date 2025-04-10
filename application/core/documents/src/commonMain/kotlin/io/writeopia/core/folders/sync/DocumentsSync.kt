package io.writeopia.core.folders.sync

import io.writeopia.common.utils.ResultData
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Clock

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
        val lastSync = documentRepository.loadDocumentById(folderId)?.lastSyncedAt ?: return

        // First, receive the documents for the backend.
        val response = documentsApi.getNewDocuments(folderId, lastSync)
        val newDocuments = if (response is ResultData.Complete) response.data else return

        // Then, load the outdated documents.
        // These documents were updated locally, but were not sent to the backend yet
        val localOutdatedDocs = documentRepository.loadOutdatedDocuments(folderId)

        // Resolve conflicts of documents that were updated both locally and in the backend.
        // Documents will be saved locally by documentConflictHandler.handleConflict
        val documentsResolved =
            documentConflictHandler.handleConflict(localOutdatedDocs, newDocuments)

        // Send documents to backend
        val resultSend = documentsApi.sendDocuments(documentsResolved)

        if (resultSend is ResultData.Complete) {
            val now = Clock.System.now()
            // If everything ran accordingly, update the sync time of the folder.
            documentsResolved.forEach { document ->
                documentRepository.saveDocumentMetadata(document.copy(lastSyncedAt = now))
            }

            documentRepository.refreshDocuments()
        } else {
            return
        }
    }
}
