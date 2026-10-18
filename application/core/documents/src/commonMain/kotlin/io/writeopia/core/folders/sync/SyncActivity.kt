package io.writeopia.core.folders.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Tells whether any sync with the backend is running, whoever started it: a folder sync, the
 * events sync or the whole workspace sync. The UI shows a spinner while it is, so the person
 * knows the app is talking to the cloud.
 */
object SyncActivity {
    private val tracker = ActivityTracker()

    val isSyncing: StateFlow<Boolean> = tracker.isActive

    /** Runs [block] as a sync: [isSyncing] holds true until it returns or throws. */
    suspend fun <T> track(block: suspend () -> T): T = tracker.track(block)
}
