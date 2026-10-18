package io.writeopia.core.folders.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Tells whether a document is being opened in the editor: read from the database, then
 * fetched from the backend and merged. The side menu shows a spinner by its folders while it
 * is, so the person knows the content they see may still change.
 */
object DocumentLoadActivity {
    private val tracker = ActivityTracker()

    val isLoading: StateFlow<Boolean> = tracker.isActive

    /** Runs [block] as a document load: [isLoading] holds true until it returns or throws. */
    suspend fun <T> track(block: suspend () -> T): T = tracker.track(block)
}
