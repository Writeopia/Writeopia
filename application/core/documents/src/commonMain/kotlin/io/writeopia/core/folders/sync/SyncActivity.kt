package io.writeopia.core.folders.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Tells whether any sync with the backend is running, whoever started it: a folder sync, the
 * events sync or the whole workspace sync. The UI shows a spinner while it is, so the person
 * knows the app is talking to the cloud.
 *
 * Syncs may overlap, so this counts the running ones instead of keeping a single flag: the
 * spinner only goes away once the last one finishes.
 */
object SyncActivity {
    private val runningSyncs = MutableStateFlow(0)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Runs [block] as a sync: [isSyncing] holds true until it returns or throws. */
    suspend fun <T> track(block: suspend () -> T): T {
        _isSyncing.value = runningSyncs.updateAndGet { it + 1 } > 0
        return try {
            block()
        } finally {
            _isSyncing.value = runningSyncs.updateAndGet { it - 1 } > 0
        }
    }
}
