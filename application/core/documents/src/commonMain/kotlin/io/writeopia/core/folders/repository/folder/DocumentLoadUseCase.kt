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
        println("DocumentLoadUseCase: Starting fetchAndMergeFromBackend for documentId=$documentId")

        // Step 1: Load current local document
        val localDocument = documentRepository.loadDocumentById(documentId, workspaceId)
        println("DocumentLoadUseCase: Local document loaded, content size=${localDocument?.content?.size}")

        // Step 2: Fetch from backend
        val backendDocument = fetchFromBackend(documentId, workspaceId)
        if (backendDocument == null) {
            println("DocumentLoadUseCase: Backend document is null, returning early")
            return
        }
        println("DocumentLoadUseCase: Backend document fetched, content size=${backendDocument.content.size}")

        // Step 3: Merge documents
        val mergedDocument = documentMerger.merge(localDocument, backendDocument)
        if (mergedDocument == null) {
            println("DocumentLoadUseCase: Merged document is null, returning early")
            return
        }
        println("DocumentLoadUseCase: Merged document created, content size=${mergedDocument.content.size}")

        // Step 4: Check if merge resulted in changes
        val hasChanges = localDocument == null || mergedDocument.content != localDocument.content
        println("DocumentLoadUseCase: hasChanges=$hasChanges")

        if (hasChanges) {
            // Step 5: Save merged result to database
            println("DocumentLoadUseCase: Saving merged document to database")
            documentRepository.saveDocument(mergedDocument)

            // Step 6: Notify that merge is complete so the UI can reload
            println("DocumentLoadUseCase: Calling onMergeComplete callback")
            onMergeComplete(mergedDocument)
        } else {
            println("DocumentLoadUseCase: No changes detected, skipping reload")
        }
    }

    private suspend fun fetchFromBackend(documentId: String, workspaceId: String): Document? {
        val token = authRepository.getAuthToken()
        if (token == null) {
            println("DocumentLoadUseCase: No auth token available, skipping backend fetch")
            return null
        }

        println("DocumentLoadUseCase: Fetching document from backend, documentId=$documentId, workspaceId=$workspaceId")

        return try {
            when (val result = documentsApi.getDocumentById(documentId, workspaceId, token)) {
                is ResultData.Complete -> {
                    println("DocumentLoadUseCase: Backend fetch successful")
                    result.data
                }
                is ResultData.Error -> {
                    println("DocumentLoadUseCase: Backend fetch returned error")
                    null
                }
                else -> {
                    println("DocumentLoadUseCase: Backend fetch returned unexpected result: $result")
                    null
                }
            }
        } catch (e: Exception) {
            // Network error, timeout, etc. - graceful degradation
            println("DocumentLoadUseCase: Failed to fetch document from backend: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
