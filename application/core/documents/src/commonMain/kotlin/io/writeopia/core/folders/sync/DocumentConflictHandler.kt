@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.time.ExperimentalTime

class DocumentConflictHandler(
    private val documentRepository: DocumentRepository
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
        val externalDocIds = externalDocuments.map { it.id }.toSet()
        val allDocumentsById = (localDocuments + externalDocuments).groupBy { it.id }

        // Resolve conflicts for each document ID.
        val resolvedDocuments = allDocumentsById.map { (id, documents) ->
            // Select the document with the newest lastUpdatedAt
            val winner = documents.maxByOrNull { it.lastUpdatedAt }
                ?: throw IllegalStateException("Document list for ID cannot be empty.")

            // Keep the winner as-is. Don't update lastSyncedAt with client time.
            // - If winner is from server, it already has the correct server lastSyncedAt
            // - If winner is local, it will get server lastSyncedAt when sent to server
            winner
        }

        // Save the resolved (winning) documents to the repository.
        resolvedDocuments.forEach { document ->
            documentRepository.saveDocument(document)
        }

        // Return documents that need to be sent to the server (local winners or local-only docs)
        return resolvedDocuments.filter { doc ->
            // Document needs to be sent if it's a local document that won the conflict
            // or if it's a local-only document (not in external)
            val isLocalOnly = doc.id !in externalDocIds
            val localDoc = localDocuments.find { it.id == doc.id }
            val isLocalWinner = localDoc != null && localDoc.lastUpdatedAt == doc.lastUpdatedAt

            isLocalOnly || isLocalWinner
        }
    }
}
