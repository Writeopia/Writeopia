@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.request.StoryStepChangeApi
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Service for syncing document story steps with the backend.
 * This service handles syncing outdated story steps (where lastUpdatedAt > lastSyncedAt).
 */
class DocumentSyncService(
    private val documentRepository: DocumentRepository,
    private val syncApi: suspend (StoryStepSyncRequest, String) -> StoryStepSyncResponse,
    private val tokenProvider: suspend () -> String?
) {

    /**
     * Syncs a document's outdated story steps with the backend.
     *
     * This function:
     * 1. Loads the document with its content
     * 2. Identifies story steps that have been modified since last sync
     * 3. Sends those steps to the backend in batches (to handle large documents)
     * 4. Applies any server updates locally
     * 5. Updates the document's lastSyncedAt
     *
     * @param documentId The ID of the document to sync
     * @param workspaceId The workspace ID
     * @return true if sync was successful, false otherwise
     */
    suspend fun syncDocument(documentId: String, workspaceId: String): Boolean {
        // Don't sync for offline workspaces - no network calls should be made
        if (workspaceId == Workspace.disconnectedWorkspace().id) {
            return false
        }

        val token = tokenProvider() ?: return false

        // Load the document with its content
        val document = documentRepository.loadDocumentById(documentId, workspaceId) ?: return false

        // Get the last sync timestamp (0 if never synced)
        val lastSyncTimestamp = document.lastSyncedAt?.toEpochMilliseconds() ?: 0L

        // Find story steps that need to be synced
        val outdatedSteps = getOutdatedStorySteps(document, lastSyncTimestamp)

        // If no steps need syncing, the document is already in sync.
        // Do NOT update lastSyncedAt here since we don't have a server timestamp.
        // The existing lastSyncedAt already matches the server's value.
        if (outdatedSteps.isEmpty()) {
            return true
        }

        // Send steps in batches to handle large documents
        return syncStepsInBatches(
            document = document,
            outdatedSteps = outdatedSteps,
            workspaceId = workspaceId,
            lastSyncTimestamp = lastSyncTimestamp,
            token = token
        )
    }

    /**
     * Sends story steps to the backend in batches.
     *
     * Large documents (especially on first sync) may have many story steps.
     * Batching prevents request payload limits and improves reliability.
     *
     * @param document The document being synced
     * @param outdatedSteps All steps that need syncing
     * @param workspaceId The workspace ID
     * @param lastSyncTimestamp The document's last sync timestamp
     * @param token The auth token
     * @return true if all batches synced successfully
     */
    private suspend fun syncStepsInBatches(
        document: Document,
        outdatedSteps: List<Pair<Double, StoryStep>>,
        workspaceId: String,
        lastSyncTimestamp: Long,
        token: String
    ): Boolean {
        val batches = outdatedSteps.chunked(MAX_STEPS_PER_BATCH)
        var currentSyncTimestamp = lastSyncTimestamp
        var lastServerTimestamp: Long? = null

        for ((batchIndex, batch) in batches.withIndex()) {
            val requestTimestamp = Clock.System.now().toEpochMilliseconds()

            val request = StoryStepSyncRequest(
                documentId = document.id,
                workspaceId = workspaceId,
                lastSyncTimestamp = currentSyncTimestamp,
                requestTimestamp = requestTimestamp,
                changes = batch.map { (position, storyStep) ->
                    StoryStepChangeApi(
                        storyStep = storyStep.toApi(position),
                        position = position
                    )
                },
                deletions = emptyList()
            )

            try {
                val response = syncApi(request, token)

                // Apply server updates locally (only on first batch to avoid duplicates)
                if (batchIndex == 0) {
                    applyServerUpdates(document, response)
                }

                // Update timestamp for next batch
                lastServerTimestamp = response.serverTimestamp
                currentSyncTimestamp = lastServerTimestamp
            } catch (e: Exception) {
                println("Error syncing document ${document.id} (batch $batchIndex): ${e.message}")
                return false
            }
        }

        // Update document's lastSyncedAt using the final SERVER timestamp
        if (lastServerTimestamp != null) {
            val serverSyncTime = Instant.fromEpochMilliseconds(lastServerTimestamp)
            val updatedDocument = document.copy(lastSyncedAt = serverSyncTime)
            documentRepository.saveDocumentMetadata(updatedDocument)
        }

        return true
    }

    /**
     * Gets story steps that have been modified since the last sync.
     *
     * A step is considered outdated if:
     * - It has a lastUpdatedAt timestamp greater than the document's lastSyncedAt
     * - The document has never been synced (lastSyncedAt is null/0)
     *
     * Note: For first-sync scenarios (lastSyncTimestamp == 0), this returns ALL steps.
     * The caller should use batching to handle large documents.
     *
     * @param document The document to check
     * @param lastSyncTimestamp The document's last sync timestamp in milliseconds
     * @return List of (position, storyStep) pairs that need syncing, sorted by position
     */
    private fun getOutdatedStorySteps(
        document: Document,
        lastSyncTimestamp: Long
    ): List<Pair<Double, StoryStep>> =
        document.content.entries
            .filter { (_, storyStep) ->
                val stepLastUpdated = storyStep.lastUpdatedAt ?: 0L
                // Include steps that were updated after the last sync
                // or if this is the first sync (lastSyncTimestamp == 0)
                stepLastUpdated > lastSyncTimestamp || lastSyncTimestamp == 0L
            }
            .map { (position, storyStep) -> position to storyStep }
            .sortedBy { it.first }

    /**
     * Applies server updates to the local document.
     *
     * @param document The local document
     * @param response The sync response from the server
     */
    private suspend fun applyServerUpdates(document: Document, response: StoryStepSyncResponse) {
        // Apply updated steps from server
        val serverSteps = response.updatedSteps.map { stepApi ->
            stepApi.position to stepApi.toModel()
        }

        if (serverSteps.isNotEmpty()) {
            // Save each server step to local storage
            for ((position, storyStep) in serverSteps) {
                documentRepository.saveStoryStep(storyStep, position, document.id)
            }
        }

        // Note: deletedIds handling would require additional logic
        // to remove steps from local storage if needed
    }

    companion object {
        /**
         * Maximum number of story steps to send in a single sync request.
         * This prevents request payload limits and improves reliability for large documents.
         */
        private const val MAX_STEPS_PER_BATCH = 50

        fun create(
            documentRepository: DocumentRepository,
            syncApi: suspend (StoryStepSyncRequest, String) -> StoryStepSyncResponse,
            tokenProvider: suspend () -> String?
        ): DocumentSyncService = DocumentSyncService(
            documentRepository = documentRepository,
            syncApi = syncApi,
            tokenProvider = tokenProvider
        )
    }
}
