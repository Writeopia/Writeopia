@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.sync.DocumentMerger
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
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
     * A step deleted on this device never comes back this way: the steps with a tombstone here
     * (deleted, and the backend not told yet) are left out of the backend's copy.
     *
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
        val fetched = fetchFromBackend(documentId, workspaceId) ?: return
        if (fetched.id != documentId || fetched.workspaceId != workspaceId) return
        val backendDocument = fetched.withoutStepsDeletedHere(workspaceId)

        // Step 3: Merge documents
        val mergedDocument = documentMerger.merge(localDocument, backendDocument) ?: return

        // Step 4: Check if merge resulted in changes
        val hasChanges = localDocument == null ||
            mergedDocument.content != localDocument.content ||
            mergedDocument.commentConversations != localDocument.commentConversations ||
            mergedDocument.tagIds != localDocument.tagIds

        if (hasChanges) {
            // Step 5: Save merged result to database
            documentRepository.saveDocument(mergedDocument)

            // Step 6: Notify that merge is complete so the UI can reload
            onMergeComplete(mergedDocument)
        }
    }

    /** The document without the steps that have a tombstone here: deleted, and the backend not told yet. */
    private suspend fun Document.withoutStepsDeletedHere(workspaceId: String): Document {
        val tombstoned = documentRepository.loadStoryStepTombstones(workspaceId)
            .map { tombstone -> tombstone.stepId }
            .toSet()
        if (tombstoned.isEmpty()) return this

        return copy(
            content = content
                .filterNot { (_, step) -> step.id in tombstoned }
                .mapValues { (_, step) -> step.withoutSteps(tombstoned) }
        )
    }

    /** The step without the nested steps with an id in [ids], at any depth. */
    private fun StoryStep.withoutSteps(ids: Set<String>): StoryStep =
        if (steps.isEmpty()) {
            this
        } else {
            copy(steps = steps.filterNot { step -> step.id in ids }.map { step -> step.withoutSteps(ids) })
        }

    private suspend fun fetchFromBackend(documentId: String, workspaceId: String): Document? =
        try {
            when (val result = documentsApi.getDocumentById(documentId, workspaceId)) {
                is ResultData.Complete -> result.data
                else -> null
            }
        } catch (e: Exception) {
            // Network error, timeout, etc. - graceful degradation
            null
        }
}
