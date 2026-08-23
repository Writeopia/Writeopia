@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.request.StoryStepChangeApi
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
     * 3. Sends those steps to the backend
     * 4. Applies any server updates locally
     * 5. Updates the document's lastSyncedAt
     *
     * @param documentId The ID of the document to sync
     * @param workspaceId The workspace ID
     * @return true if sync was successful, false otherwise
     */
    suspend fun syncDocument(documentId: String, workspaceId: String): Boolean {
        val token = tokenProvider() ?: return false

        // Load the document with its content
        val document = documentRepository.loadDocumentById(documentId, workspaceId) ?: return false

        // Get the last sync timestamp (0 if never synced)
        val lastSyncTimestamp = document.lastSyncedAt?.toEpochMilliseconds() ?: 0L

        // Find story steps that need to be synced
        val outdatedSteps = getOutdatedStorySteps(document, lastSyncTimestamp)

        // If no steps need syncing, just update the lastSyncedAt
        if (outdatedSteps.isEmpty()) {
            val now = Clock.System.now()
            val updatedDocument = document.copy(lastSyncedAt = now)
            documentRepository.saveDocumentMetadata(updatedDocument)
            return true
        }

        val requestTimestamp = Clock.System.now().toEpochMilliseconds()

        // Build the sync request
        val request = StoryStepSyncRequest(
            documentId = documentId,
            workspaceId = workspaceId,
            lastSyncTimestamp = lastSyncTimestamp,
            requestTimestamp = requestTimestamp,
            changes = outdatedSteps.map { (position, storyStep) ->
                StoryStepChangeApi(
                    storyStep = storyStep.toApi(position),
                    position = position
                )
            },
            deletions = emptyList() // We don't track deletions here - they're handled elsewhere
        )

        return try {
            val response = syncApi(request, token)

            // Apply server updates locally
            applyServerUpdates(document, response)

            // Update document's lastSyncedAt
            val now = Clock.System.now()
            val updatedDocument = document.copy(lastSyncedAt = now)
            documentRepository.saveDocumentMetadata(updatedDocument)

            true
        } catch (e: Exception) {
            println("Error syncing document $documentId: ${e.message}")
            false
        }
    }

    /**
     * Gets story steps that have been modified since the last sync.
     *
     * A step is considered outdated if:
     * - It has a lastUpdatedAt timestamp greater than the document's lastSyncedAt
     * - The document has never been synced (lastSyncedAt is null)
     *
     * @param document The document to check
     * @param lastSyncTimestamp The document's last sync timestamp in milliseconds
     * @return List of (position, storyStep) pairs that need syncing
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
