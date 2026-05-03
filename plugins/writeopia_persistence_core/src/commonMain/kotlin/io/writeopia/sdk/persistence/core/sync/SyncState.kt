package io.writeopia.sdk.persistence.core.sync

/**
 * Represents the current state of backend synchronization.
 */
enum class SyncState {
    /**
     * All changes have been synced to the backend.
     */
    SYNCED,

    /**
     * Changes are pending and waiting for debounce period.
     */
    PENDING,

    /**
     * Currently syncing to the backend.
     */
    SYNCING,

    /**
     * Last sync attempt failed. Will retry automatically.
     */
    ERROR
}
