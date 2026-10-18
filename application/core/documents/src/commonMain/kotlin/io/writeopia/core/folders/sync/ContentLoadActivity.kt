package io.writeopia.core.folders.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Tells whether content picked in the side menu is being loaded: a document opening in the
 * editor (read from the database, then fetched from the backend and merged) or a folder
 * expanding (its children fetched from the backend). The side menu shows a spinner by its
 * folders while it is, so the person knows what they see may still change.
 */
object ContentLoadActivity {
    private val tracker = ActivityTracker()

    val isLoading: StateFlow<Boolean> = tracker.isActive

    /** Runs [block] as a content load: [isLoading] holds true until it returns or throws. */
    suspend fun <T> track(block: suspend () -> T): T = tracker.track(block)
}
