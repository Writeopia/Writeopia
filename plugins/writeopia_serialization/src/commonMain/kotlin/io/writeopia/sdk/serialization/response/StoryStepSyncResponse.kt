package io.writeopia.sdk.serialization.response

import io.writeopia.sdk.serialization.data.StoryStepApi
import kotlinx.serialization.Serializable

/**
 * Response payload for StoryStep sync operations.
 *
 * @param serverTimestamp The server's timestamp at the time of processing (epoch milliseconds).
 *                        Use this for the next sync's lastSyncTimestamp.
 * @param updatedSteps List of StorySteps that were updated on the server after the client's lastSyncTimestamp.
 * @param deletedIds List of StoryStep IDs that were deleted on the server.
 */
@Serializable
data class StoryStepSyncResponse(
    val serverTimestamp: Long,
    val updatedSteps: List<StoryStepApi>,
    val deletedIds: List<String>
)
