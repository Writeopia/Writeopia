@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.tracker

import io.writeopia.sdk.manager.StoryStepSyncTracker
import io.writeopia.sdk.manager.sync.MetadataChange
import io.writeopia.sdk.manager.sync.StoryStepSyncBuffer
import io.writeopia.sdk.manager.sync.SyncBatch
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.story.StoryStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Interface for the API that syncs story steps with the backend.
 */
interface StoryStepSyncApi {
    /**
     * Syncs story step changes with the backend.
     *
     * @param documentId The document being synced
     * @param workspaceId The workspace containing the document
     * @param lastSyncTimestamp The timestamp of the last successful sync
     * @param modifiedSteps Steps that have been modified locally
     * @param deletedStepIds IDs of steps that have been deleted locally
     * @param metadataUpdate Metadata changes to sync (title, icon, favorite, etc.)
     * @return Sync result with server timestamp, updated steps, and any server-side changes
     */
    suspend fun syncStorySteps(
        documentId: String,
        workspaceId: String,
        lastSyncTimestamp: Long,
        modifiedSteps: List<Pair<StoryStep, Double>>,
        deletedStepIds: List<String>,
        metadataUpdate: MetadataChange? = null
    ): StoryStepSyncResult?
}

data class StoryStepSyncResult(
    val serverTimestamp: Long,
    val updatedSteps: List<StoryStep>,
    val deletedStepIds: List<String>,
    val metadataUpdate: MetadataChange? = null
)

/**
 * Implementation of StoryStepSyncTracker that tracks story step changes and syncs them with the backend.
 * Follows the pattern established by OnUpdateDocumentTracker.
 *
 * @param syncApi The API client for syncing with the backend
 * @param coroutineScope The scope for launching coroutines
 * @param pollIntervalMs The interval between remote change polls (default 3000ms)
 * @param batchWindowMs The batching window for local changes (default 500ms)
 */
class OnUpdateStoryStepTracker(
    private val syncApi: StoryStepSyncApi,
    private val coroutineScope: CoroutineScope,
    private val pollIntervalMs: Long = 3000L,
    private val batchWindowMs: Long = 500L
) : StoryStepSyncTracker {

    private var syncBuffer: StoryStepSyncBuffer? = null
    private var stateCollectionJob: Job? = null
    private var metadataCollectionJob: Job? = null
    private var batchSyncJob: Job? = null
    private var pollJob: Job? = null

    private var currentDocumentId: String? = null
    private var currentWorkspaceId: String? = null
    private var lastSyncTimestamp: Long = 0L
    private var lastRemoteMetadataTimestamp: Long = 0L

    override suspend fun startSyncing(
        documentId: String,
        workspaceId: String,
        storyStateFlow: Flow<StoryState>,
        documentInfoFlow: Flow<DocumentInfo>,
        onRemoteUpdate: suspend (List<StoryStep>, List<String>) -> Unit,
        onRemoteMetadataUpdate: suspend (MetadataChange) -> Unit
    ) {
        // Stop any existing sync
        stopSyncing()

        currentDocumentId = documentId
        currentWorkspaceId = workspaceId
        lastSyncTimestamp = Clock.System.now().toEpochMilliseconds()

        // Create a new sync buffer
        syncBuffer = StoryStepSyncBuffer(coroutineScope, batchWindowMs)

        // Start collecting story state changes
        stateCollectionJob = coroutineScope.launch {
            storyStateFlow.collect { storyState ->
                handleStoryStateChange(storyState)
            }
        }

        // Start collecting metadata changes
        // Drop the first emission (initial load) to avoid syncing stale database data
        // Skip emissions that match the last remote metadata timestamp (to avoid feedback loop)
        metadataCollectionJob = coroutineScope.launch {
            var isFirstEmission = true

            documentInfoFlow
                .collect { docInfo ->
                    val currentTimestamp = docInfo.lastUpdatedAt.toEpochMilliseconds()

                    // Skip first emission (initial document load)
                    if (isFirstEmission) {
                        isFirstEmission = false
                        return@collect
                    }

                    // Skip if this timestamp matches the last remote update we applied
                    // This prevents syncing back remote updates to the server
                    if (currentTimestamp == lastRemoteMetadataTimestamp) {
                        return@collect
                    }

                    syncBuffer?.trackMetadataChange(
                        title = docInfo.title,
                        icon = docInfo.icon?.label,
                        iconTint = docInfo.icon?.tint,
                        favorite = docInfo.isFavorite
                    )
                }
        }

        // Start collecting batched changes and syncing
        batchSyncJob = coroutineScope.launch {
            syncBuffer?.syncBatch?.collect { batch ->
                handleBatchSync(batch, onRemoteUpdate, onRemoteMetadataUpdate)
            }
        }

        // Start periodic polling for remote changes
        pollJob = coroutineScope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                pollForRemoteChanges(onRemoteUpdate, onRemoteMetadataUpdate)
            }
        }
    }

    override suspend fun stopSyncing() {
        // Force flush any pending changes before stopping
        syncBuffer?.forceFlush()

        stateCollectionJob?.cancel()
        metadataCollectionJob?.cancel()
        batchSyncJob?.cancel()
        pollJob?.cancel()

        stateCollectionJob = null
        metadataCollectionJob = null
        batchSyncJob = null
        pollJob = null
        syncBuffer = null

        currentDocumentId = null
        currentWorkspaceId = null
    }

    override suspend fun forceSync() {
        syncBuffer?.forceFlush()
    }

    private fun handleStoryStateChange(storyState: StoryState) {
        val buffer = syncBuffer ?: return

        when (val lastEdit = storyState.lastEdit) {
            is LastEdit.LineEdition -> {
                if (!lastEdit.storyStep.ephemeral) {
                    buffer.trackChange(lastEdit.storyStep, lastEdit.position)
                }
            }

            is LastEdit.BulkEdition -> {
                lastEdit.steps
                    .filter { (_, step) -> !step.ephemeral }
                    .forEach { (position, step) ->
                        buffer.trackChange(step, position)
                    }
            }

            is LastEdit.LineBreakEdition -> {
                val (origPos, origStep) = lastEdit.originalStep
                val (newPos, newStep) = lastEdit.newStep

                if (!origStep.ephemeral) {
                    buffer.trackChange(origStep, origPos)
                }
                if (!newStep.ephemeral) {
                    buffer.trackChange(newStep, newPos)
                }
            }

            is LastEdit.DeleteEdition -> {
                buffer.trackDeletion(lastEdit.deletedId)
            }

            is LastEdit.EraseEdition -> {
                buffer.trackDeletion(lastEdit.deletedId)
                val (pos, step) = lastEdit.updatedStep
                if (!step.ephemeral) {
                    buffer.trackChange(step, pos)
                }
            }

            is LastEdit.BulkDeleteEdition -> {
                lastEdit.deletedIds.forEach { id ->
                    buffer.trackDeletion(id)
                }
                // Also track any updated steps (e.g., position reference changes)
                lastEdit.updatedSteps.forEach { (position, step) ->
                    if (!step.ephemeral) {
                        buffer.trackChange(step, position)
                    }
                }
            }

            is LastEdit.InfoEdition -> {
                if (!lastEdit.storyStep.ephemeral) {
                    buffer.trackChange(lastEdit.storyStep, lastEdit.position)
                }
            }

            LastEdit.Nothing,
            LastEdit.Whole,
            LastEdit.Metadata -> {
                // Nothing to track for these edit types
            }
        }
    }

    private suspend fun handleBatchSync(
        batch: SyncBatch,
        onRemoteUpdate: suspend (List<StoryStep>, List<String>) -> Unit,
        onRemoteMetadataUpdate: suspend (MetadataChange) -> Unit
    ) {
        val docId = currentDocumentId ?: return
        val wsId = currentWorkspaceId ?: return

        if (batch.changes.isEmpty() && batch.deletions.isEmpty() && batch.metadata == null) return

        val modifiedSteps = batch.changes.map { change ->
            change.storyStep to change.position
        }

        val result = syncApi.syncStorySteps(
            documentId = docId,
            workspaceId = wsId,
            lastSyncTimestamp = lastSyncTimestamp,
            modifiedSteps = modifiedSteps,
            deletedStepIds = batch.deletions.toList(),
            metadataUpdate = batch.metadata
        )

        if (result != null) {
            lastSyncTimestamp = result.serverTimestamp

            // Notify about remote updates if any
            if (result.updatedSteps.isNotEmpty() || result.deletedStepIds.isNotEmpty()) {
                onRemoteUpdate(result.updatedSteps, result.deletedStepIds)
            }

            // Notify about remote metadata updates if server won
            result.metadataUpdate?.let { metadata ->
                // Track this timestamp so we don't sync it back
                lastRemoteMetadataTimestamp = metadata.lastUpdatedAt
                onRemoteMetadataUpdate(metadata)
            }
        }
    }

    private suspend fun pollForRemoteChanges(
        onRemoteUpdate: suspend (List<StoryStep>, List<String>) -> Unit,
        onRemoteMetadataUpdate: suspend (MetadataChange) -> Unit
    ) {
        val docId = currentDocumentId ?: return
        val wsId = currentWorkspaceId ?: return

        // Poll with empty changes to just get remote updates
        val result = syncApi.syncStorySteps(
            documentId = docId,
            workspaceId = wsId,
            lastSyncTimestamp = lastSyncTimestamp,
            modifiedSteps = emptyList(),
            deletedStepIds = emptyList(),
            metadataUpdate = null
        )

        if (result != null) {
            lastSyncTimestamp = result.serverTimestamp

            if (result.updatedSteps.isNotEmpty() || result.deletedStepIds.isNotEmpty()) {
                onRemoteUpdate(result.updatedSteps, result.deletedStepIds)
            }

            // Notify about remote metadata updates if any
            result.metadataUpdate?.let { metadata ->
                // Track this timestamp so we don't sync it back
                lastRemoteMetadataTimestamp = metadata.lastUpdatedAt
                onRemoteMetadataUpdate(metadata)
            }
        }
    }
}
