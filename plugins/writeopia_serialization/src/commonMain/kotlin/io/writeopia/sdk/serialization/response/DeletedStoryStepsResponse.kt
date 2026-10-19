package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

/**
 * The ids of the steps deleted in a workspace since a given time.
 *
 * @param serverTimestamp The server's time when the list was read (epoch milliseconds).
 */
@Serializable
data class DeletedStoryStepsResponse(
    val stepIds: List<String>,
    val serverTimestamp: Long
)
