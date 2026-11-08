package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

/**
 * How much media a user keeps on the backend. [version] is the version of the row the numbers
 * were read from: it grows with every change, so a client can tell a fresh answer from a stale one.
 */
@Serializable
data class StorageUsageResponse(
    val usedBytes: Long,
    val quotaBytes: Long,
    val version: Long
)
