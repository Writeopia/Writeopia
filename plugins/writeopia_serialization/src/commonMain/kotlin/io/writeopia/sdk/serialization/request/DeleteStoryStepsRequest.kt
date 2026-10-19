package io.writeopia.sdk.serialization.request

import io.writeopia.sdk.serialization.data.StoryStepTombstoneApi
import kotlinx.serialization.Serializable

/**
 * The steps a device deleted, sent before its content so the backend never resurrects them.
 * Many documents at once: only ids travel, so a request can carry a lot of them.
 */
@Serializable
data class DeleteStoryStepsRequest(
    val tombstones: List<StoryStepTombstoneApi>
)
