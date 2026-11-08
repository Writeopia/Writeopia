package io.writeopia.storage

/** How much media a user may keep. */
object StorageQuota {
    /** 20 GiB, the space of every user. */
    const val DEFAULT_BYTES: Long = 20L * 1024 * 1024 * 1024

    /** The quota of the deployment: `STORAGE_QUOTA_BYTES` when set, else [DEFAULT_BYTES]. */
    fun bytes(): Long = System.getenv("STORAGE_QUOTA_BYTES")?.toLongOrNull() ?: DEFAULT_BYTES
}
