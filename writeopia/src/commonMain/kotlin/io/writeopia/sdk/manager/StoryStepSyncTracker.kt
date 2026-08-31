package io.writeopia.sdk.manager

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.StoryState
import kotlinx.coroutines.flow.Flow

/**
 * Interface for syncing StorySteps with a backend.
 *
 * Implementations should listen to document edition events and sync changes
 * to the backend using a buffered approach to avoid excessive requests.
 */
interface StoryStepSyncTracker {

    /**
     * Starts syncing StorySteps with the backend.
     *
     * This method should:
     * 1. Listen to documentEditionFlow for changes
     * 2. Buffer changes to avoid excessive requests
     * 3. Send batched changes to the backend
     * 4. Apply server updates only if they are newer than local changes
     *
     * @param documentEditionFlow Flow emitting the document state and info on each change.
     * @param workspaceIdFlow Flow emitting the current workspace ID.
     */
    suspend fun syncStorySteps(
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
        workspaceIdFlow: Flow<String>
    )
}
