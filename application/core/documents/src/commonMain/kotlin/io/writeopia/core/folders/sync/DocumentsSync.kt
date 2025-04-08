package io.writeopia.core.folders.sync

import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Instant

class DocumentsSync(private val documentRepository: DocumentRepository) {

    /**
     * Sync the folder with the backend end. The lastSync should be data fetched from the backend.
     */
    suspend fun syncFolders(folderId: String, lastSync: Instant) {
        //First, receive the documents for the backend.

        // Then, load the outdated documents.
        // These documents were updated locally, but were not sent to the backend yet
        val documentThatNeedToBeSent =
            documentRepository.loadOutdatedDocuments(folderId)

        // Resolve conflicts of documents that were updated both locally and in the backend.


    }
}
