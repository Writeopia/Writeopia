@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.manager.sync

import io.writeopia.sdk.models.story.StoryStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Represents a change to a StoryStep that needs to be synced.
 */
data class StoryStepChange(
    val storyStep: StoryStep,
    val position: Double,
    val timestamp: Long
)

/**
 * Represents a change to document metadata that needs to be synced.
 */
data class MetadataChange(
    val title: String?,
    val icon: String?,
    val iconTint: Int?,
    val favorite: Boolean?,
    val lastUpdatedAt: Long
)

/**
 * Represents a batch of changes ready to be sent to the server.
 */
data class SyncBatch(
    val changes: List<StoryStepChange>,
    val deletions: Set<String>,
    val metadata: MetadataChange? = null
)

/**
 * Buffers StoryStep changes and emits batched changes after a delay window.
 * This reduces the number of sync requests by grouping rapid edits together.
 *
 * @param scope The coroutine scope for managing the batching timer
 * @param batchWindowMs The delay in milliseconds before flushing changes (default 500ms)
 */
class StoryStepSyncBuffer(
    private val scope: CoroutineScope,
    private val batchWindowMs: Long = 500L
) {
    private val mutex = Mutex()
    private val pendingChanges = mutableMapOf<String, StoryStepChange>()
    private val pendingDeletions = mutableSetOf<String>()
    private var pendingMetadata: MetadataChange? = null

    private val _syncBatch = MutableSharedFlow<SyncBatch>(extraBufferCapacity = 64)
    val syncBatch: Flow<SyncBatch> = _syncBatch.asSharedFlow()

    private var flushJob: Job? = null

    /**
     * Tracks a change to a StoryStep. The step will be included in the next batch.
     * If the same step is modified multiple times before the batch is sent,
     * only the latest version is included.
     *
     * @param step The modified StoryStep
     * @param position The position of the step in the document
     */
    fun trackChange(step: StoryStep, position: Double) {
        scope.launch {
            mutex.withLock {
                val timestamp = Clock.System.now().toEpochMilliseconds()
                val change = StoryStepChange(
                    storyStep = step.copy(lastUpdatedAt = timestamp),
                    position = position,
                    timestamp = timestamp
                )
                pendingChanges[step.id] = change
                // If step was marked for deletion, remove it from deletions
                pendingDeletions.remove(step.id)
                scheduleBatchFlush()
            }
        }
    }

    /**
     * Tracks a deletion of a StoryStep.
     *
     * @param stepId The ID of the step to delete
     */
    fun trackDeletion(stepId: String) {
        scope.launch {
            mutex.withLock {
                pendingDeletions.add(stepId)
                // Remove any pending changes for this step
                pendingChanges.remove(stepId)
                scheduleBatchFlush()
            }
        }
    }

    /**
     * Tracks a change to document metadata.
     *
     * @param title The new document title, or null if unchanged
     * @param icon The new document icon, or null if unchanged
     * @param iconTint The new icon tint color, or null if unchanged
     * @param favorite The new favorite status, or null if unchanged
     */
    fun trackMetadataChange(
        title: String? = null,
        icon: String? = null,
        iconTint: Int? = null,
        favorite: Boolean? = null
    ) {
        scope.launch {
            mutex.withLock {
                val timestamp = Clock.System.now().toEpochMilliseconds()
                pendingMetadata = MetadataChange(title, icon, iconTint, favorite, timestamp)
                scheduleBatchFlush()
            }
        }
    }

    /**
     * Immediately flushes all pending changes without waiting for the batch window.
     * Useful when closing a document to ensure all changes are sent.
     */
    suspend fun forceFlush() {
        mutex.withLock {
            flushJob?.cancel()
            flushJob = null
            emitBatch()
        }
    }

    /**
     * Clears all pending changes without emitting them.
     */
    suspend fun clear() {
        mutex.withLock {
            flushJob?.cancel()
            flushJob = null
            pendingChanges.clear()
            pendingDeletions.clear()
            pendingMetadata = null
        }
    }

    /**
     * Returns true if there are pending changes, deletions, or metadata changes.
     */
    suspend fun hasPendingChanges(): Boolean = mutex.withLock {
        pendingChanges.isNotEmpty() || pendingDeletions.isNotEmpty() || pendingMetadata != null
    }

    private fun scheduleBatchFlush() {
        // Cancel existing job to restart the window
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(batchWindowMs)
            mutex.withLock {
                emitBatch()
            }
        }
    }

    private suspend fun emitBatch() {
        if (pendingChanges.isEmpty() && pendingDeletions.isEmpty() && pendingMetadata == null) return

        val batch = SyncBatch(
            changes = pendingChanges.values.toList(),
            deletions = pendingDeletions.toSet(),
            metadata = pendingMetadata
        )

        pendingChanges.clear()
        pendingDeletions.clear()
        pendingMetadata = null

        _syncBatch.emit(batch)
    }
}
