package io.writeopia.sdk.serialization.request

import io.writeopia.sdk.serialization.data.StoryStepApi
import kotlinx.serialization.Serializable

/**
 * Request payload for syncing StorySteps between client and backend.
 *
 * @param documentId The ID of the document being synced.
 * @param workspaceId The ID of the workspace containing the document.
 * @param lastSyncTimestamp The client's last sync timestamp (epoch milliseconds).
 * @param requestTimestamp A single timestamp for the entire request (epoch milliseconds).
 * @param changes List of StoryStep changes to sync.
 * @param deletions List of StoryStep IDs that were deleted.
 */
@Serializable
data class StoryStepSyncRequest(
    val documentId: String,
    val workspaceId: String,
    val lastSyncTimestamp: Long,
    val requestTimestamp: Long,
    val changes: List<StoryStepChangeApi>,
    val deletions: List<String>
)

/**
 * Represents a single StoryStep change in a sync request.
 *
 * @param storyStep The StoryStep data.
 * @param position The position of the StoryStep in the document.
 */
@Serializable
data class StoryStepChangeApi(
    val storyStep: StoryStepApi,
    val position: Double
)
