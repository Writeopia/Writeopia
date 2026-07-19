package io.writeopia.sdk.network.api

import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse

/**
 * API interface for syncing StorySteps with the backend.
 */
interface StoryStepSyncApi {

    /**
     * Syncs StorySteps with the backend.
     *
     * @param request The sync request containing changes, deletions, and timestamps.
     * @return The sync response containing server updates and new timestamp.
     */
    suspend fun syncStorySteps(request: StoryStepSyncRequest): StoryStepSyncResponse
}
