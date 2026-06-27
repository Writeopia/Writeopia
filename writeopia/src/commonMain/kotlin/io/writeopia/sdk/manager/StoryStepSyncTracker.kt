package io.writeopia.sdk.manager

import io.writeopia.sdk.manager.sync.MetadataChange
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.story.StoryStep
import kotlinx.coroutines.flow.Flow

/**
 * Tracks and syncs individual StoryStep changes with the backend in real-time.
 * Similar to DocumentTracker but operates at the step level for more granular sync.
 */
interface StoryStepSyncTracker {

    /**
     * Starts syncing story steps for a document.
     *
     * @param documentId The ID of the document being edited
     * @param workspaceId The workspace containing the document
     * @param storyStateFlow Flow emitting story state changes
     * @param documentInfoFlow Flow emitting document info changes (title, icon, favorite, etc.)
     * @param onRemoteUpdate Callback invoked when remote updates are received from server
     * @param onRemoteMetadataUpdate Callback invoked when remote metadata updates are received
     */
    suspend fun startSyncing(
        documentId: String,
        workspaceId: String,
        storyStateFlow: Flow<StoryState>,
        documentInfoFlow: Flow<DocumentInfo>,
        onRemoteUpdate: suspend (List<StoryStep>, List<String>) -> Unit,
        onRemoteMetadataUpdate: suspend (MetadataChange) -> Unit
    )

    /**
     * Stops syncing and releases resources.
     */
    suspend fun stopSyncing()

    /**
     * Forces an immediate sync of pending changes.
     * Useful when closing a document to ensure all changes are persisted.
     */
    suspend fun forceSync()
}
