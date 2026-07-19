package io.writeopia.sdk.persistence.core.sync

import io.writeopia.sdk.models.story.StoryStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A buffer that collects StoryStep changes and provides debounced sync triggers.
 *
 * This buffer is used to batch rapid changes to StorySteps before syncing with the backend,
 * avoiding excessive network requests during typing or rapid edits.
 *
 * @param syncIntervalMs The debounce interval in milliseconds before triggering a sync.
 */
@OptIn(ExperimentalTime::class)
class StoryStepSyncBuffer(
    private val syncIntervalMs: Long = 500L
) {
    private val pendingChanges = mutableMapOf<String, PendingStoryStepChange>()
    private val pendingDeletions = mutableSetOf<String>()
    private val _syncTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Represents a pending change to a StoryStep that hasn't been synced yet.
     */
    data class PendingStoryStepChange(
        val storyStep: StoryStep,
        val position: Double,
        val documentId: String,
        val timestamp: Long
    )

    /**
     * Represents a batch of changes to be synced.
     */
    data class SyncBatch(
        val changes: List<PendingStoryStepChange>,
        val deletions: Set<String>
    ) {
        val isEmpty: Boolean get() = changes.isEmpty() && deletions.isEmpty()
    }

    /**
     * Adds a StoryStep change to the buffer.
     * If a change for the same StoryStep already exists, it will be replaced.
     *
     * @param storyStep The StoryStep that was changed.
     * @param position The position of the StoryStep in the document.
     * @param documentId The ID of the document containing the StoryStep.
     */
    fun addChange(storyStep: StoryStep, position: Double, documentId: String) {
        // Remove from deletions if it was previously marked for deletion
        pendingDeletions.remove(storyStep.id)

        pendingChanges[storyStep.id] = PendingStoryStepChange(
            storyStep = storyStep,
            position = position,
            documentId = documentId,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )

        _syncTrigger.tryEmit(Unit)
    }

    /**
     * Adds a StoryStep deletion to the buffer.
     *
     * @param storyStepId The ID of the StoryStep to delete.
     */
    fun addDeletion(storyStepId: String) {
        // Remove from changes if it was previously changed
        pendingChanges.remove(storyStepId)
        pendingDeletions.add(storyStepId)

        _syncTrigger.tryEmit(Unit)
    }

    /**
     * Consumes all pending changes and deletions, returning them as a batch.
     * After calling this method, the buffer will be empty.
     *
     * @return A SyncBatch containing all pending changes and deletions.
     */
    fun consumeChanges(): SyncBatch {
        val changes = pendingChanges.values.toList()
        val deletions = pendingDeletions.toSet()

        pendingChanges.clear()
        pendingDeletions.clear()

        return SyncBatch(changes, deletions)
    }

    /**
     * Checks if there are any pending changes or deletions.
     */
    fun hasPendingChanges(): Boolean = pendingChanges.isNotEmpty() || pendingDeletions.isNotEmpty()

    /**
     * Flow that emits when changes are added to the buffer.
     * Use with debounce to trigger syncing after a period of inactivity.
     */
    val syncTrigger: Flow<Unit> = _syncTrigger

    /**
     * The sync interval in milliseconds for debouncing.
     */
    val syncInterval: Long get() = syncIntervalMs
}
