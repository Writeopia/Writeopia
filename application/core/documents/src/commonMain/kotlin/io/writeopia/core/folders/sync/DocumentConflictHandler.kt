package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Clock

class DocumentConflictHandler(
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository
) {

    /**
     * Handle conflicts with documents that were updated both locally and in the backend.
     *
     * @param localDocuments The documents that should be sent to the backend, because they were updated locally
     * @param externalDocuments The documents that should be updated locally, because they were updated in the backend.
     *
     * @return The documents that are still to be sent to the cloud.
     */
    suspend fun handleConflict(
        localDocuments: List<Document>,
        externalDocuments: List<Document>
    ): List<Document> {
        val now = Clock.System.now()
        // Todo: Implement!! Save external documents and remove localDocuments. A more complex
        // handling of conflicts can be implemented in the future.
        externalDocuments.forEach { document ->
            documentRepository.saveDocument(document.copy(lastSyncedAt = now))
        }

        return (localDocuments.toSet() - externalDocuments.toSet()).toList()
    }
}
