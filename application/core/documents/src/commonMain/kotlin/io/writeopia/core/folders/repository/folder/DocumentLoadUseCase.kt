@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.sync.DocumentMerger
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.time.ExperimentalTime

/**
 * UseCase that orchestrates loading documents with merge support from both
 * local database and backend.
 */
class DocumentLoadUseCase(
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentMerger: DocumentMerger,
    private val authRepository: AuthRepository
) {

    /**
     * Fetches document from backend, merges with local, saves to database, and
     * calls the onMergeComplete callback if there were changes to reload.
     *
     * This should be called in the background after the local document is already
     * displayed to the user.
     *
     * @param documentId The ID of the document to fetch
     * @param workspaceId The workspace ID
     * @param onMergeComplete Callback with the merged document if backend had changes
     */
    suspend fun fetchAndMergeFromBackend(
        documentId: String,
        workspaceId: String,
        onMergeComplete: suspend (Document) -> Unit
    ) {
        // Step 1: Load current local document
        val localDocument = documentRepository.loadDocumentById(documentId, workspaceId)

        // Step 2: Fetch from backend
        val backendDocument = fetchFromBackend(documentId, workspaceId) ?: return

        // Step 3: Merge documents
        val mergedDocument = documentMerger.merge(localDocument, backendDocument) ?: return

        // Step 4: Check if merge resulted in changes
        val hasChanges = localDocument == null || mergedDocument.content != localDocument.content

        if (hasChanges) {
            // Step 5: Save merged result to database
            documentRepository.saveDocument(mergedDocument)

            // Step 6: Notify that merge is complete so the UI can reload
            onMergeComplete(mergedDocument)
        }
    }

    private suspend fun fetchFromBackend(documentId: String, workspaceId: String): Document? {
        val token = authRepository.getAuthToken() ?: return null

        return try {
            when (val result = documentsApi.getDocumentById(documentId, workspaceId, token)) {
                is ResultData.Complete -> result.data
                else -> null
            }
        } catch (e: Exception) {
            // Network error, timeout, etc. - graceful degradation
            null
        }
    }
}
