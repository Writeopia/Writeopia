package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Clock

class DocumentConflictHandler(
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository,
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

        val allDocumentsById = (localDocuments + externalDocuments).groupBy { it.id }

        // Resolve conflicts for each document ID.
        val resolvedDocuments = allDocumentsById.map { (_, documents) ->
            // Select the document with the newest lastUpdatedAt
            val winner = documents.maxByOrNull { it.lastUpdatedAt }
                ?: throw IllegalStateException("Document list for ID cannot be empty.")

            // Mark the winner as synced (or keep existing lastSyncedAt if it's external and already set)
            winner.copy(lastSyncedAt = now)
        }

        // Save the resolved (winning and synced) documents to the repository.
        resolvedDocuments.forEach { document ->
            documentRepository.saveDocument(document)
        }

        // Determine which documents to return.
        return resolvedDocuments
    }

    suspend fun handleConflictForFolders(
        localFolders: List<Folder>,
        externalFolders: List<Folder>,
    ): List<Folder> {
        val now = Clock.System.now()

        externalFolders.forEach { folder ->
            folderRepository.updateFolder(folder.copy(lastSyncedAt = now))
        }

        return (localFolders.toSet() - externalFolders.toSet()).toList()
    }
}
