package io.writeopia.core.folders.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Tells whether any tracked activity is running, whoever started it. The UI shows a spinner
 * while one is.
 *
 * Activities may overlap, so this counts the running ones instead of keeping a single flag:
 * [isActive] only goes back to false once the last one finishes.
 */
class ActivityTracker {
    private val running = MutableStateFlow(0)

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /** Runs [block] as an activity: [isActive] holds true until it returns or throws. */
    suspend fun <T> track(block: suspend () -> T): T {
        _isActive.value = running.updateAndGet { it + 1 } > 0
        return try {
            block()
        } finally {
            _isActive.value = running.updateAndGet { it - 1 } > 0
        }
    }
}
