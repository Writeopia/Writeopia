package io.writeopia.storage

/**
 * The media a user keeps, as read from the database. [version] is the optimistic lock of the
 * row: a change is written with the version it was computed from, and only lands when the row
 * still has it.
 */
data class StorageUsage(
    val userId: String,
    val usedBytes: Long,
    val quotaBytes: Long,
    val version: Long
) {
    val freeBytes: Long get() = (quotaBytes - usedBytes).coerceAtLeast(0)

    /** Whether [bytes] more would still fit in the quota. */
    fun fits(bytes: Long): Boolean = usedBytes + bytes <= quotaBytes
}

/** The outcome of changing the usage of a user. */
sealed class StorageChange {
    /** The change landed; [usage] is the row after it. */
    data class Applied(val usage: StorageUsage) : StorageChange()

    /** Nothing changed: [requestedBytes] more would overflow the quota. */
    data class QuotaExceeded(val usage: StorageUsage, val requestedBytes: Long) : StorageChange()

    /** Nothing changed: every attempt found the row changed by someone else first. */
    data class Conflict(val attempts: Int) : StorageChange()
}
