package io.writeopia.sdk.persistence.core.sync

import io.writeopia.sdk.manager.DocumentTracker
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.StoryState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A global document sync manager that handles syncing documents with the backend.
 *
 * This manager uses a global coroutine scope that is not tied to any ViewModel lifecycle,
 * ensuring that document syncing continues even when the user leaves the editor.
 *
 * Usage:
 * 1. Register a document for syncing when the editor opens using [registerForSync]
 * 2. The sync will continue even after the ViewModel is cleared
 * 3. Call [unregisterFromSync] when you want to explicitly stop syncing a document
 */
class DocumentSyncManager(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val activeSyncJobs = mutableMapOf<String, Job>()

    /**
     * Registers a document for syncing with the backend.
     *
     * The sync will continue using the global coroutine scope, so it won't be cancelled
     * when the ViewModel is cleared. This ensures that all pending changes are synced
     * even if the user leaves the editor.
     *
     * @param documentId The unique identifier of the document to sync
     * @param documentEditionFlow Flow emitting the document state and info on each change
     * @param workspaceIdFlow Flow emitting the current workspace ID
     * @param documentTracker The tracker responsible for saving document changes
     */
    fun registerForSync(
        documentId: String,
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
        workspaceIdFlow: Flow<String>,
        documentTracker: DocumentTracker
    ) {
        // Cancel any existing sync for this document
        activeSyncJobs[documentId]?.cancel()

        // Start a new sync job in the global scope
        val job = scope.launch(dispatcher) {
            documentTracker.saveOnStoryChanges(
                documentEditionFlow,
                workspaceIdFlow
            )
        }

        activeSyncJobs[documentId] = job
    }

    /**
     * Unregisters a document from syncing.
     *
     * This will cancel the sync job for the specified document.
     * Note: Any pending sync operations may not complete if cancelled.
     *
     * @param documentId The unique identifier of the document to stop syncing
     */
    fun unregisterFromSync(documentId: String) {
        activeSyncJobs[documentId]?.cancel()
        activeSyncJobs.remove(documentId)
    }

    /**
     * Checks if a document is currently registered for syncing.
     *
     * @param documentId The unique identifier of the document
     * @return true if the document is currently being synced
     */
    fun isSyncing(documentId: String): Boolean {
        val job = activeSyncJobs[documentId]
        return job != null && job.isActive
    }

    /**
     * Returns the number of documents currently being synced.
     */
    fun activeSyncCount(): Int = activeSyncJobs.values.count { it.isActive }

    /**
     * Cancels all active sync jobs.
     *
     * Use with caution - this may result in data loss if there are pending changes.
     */
    fun cancelAllSync() {
        activeSyncJobs.values.forEach { it.cancel() }
        activeSyncJobs.clear()
    }

    companion object {
        private var instance: DocumentSyncManager? = null

        /**
         * Gets the singleton instance of DocumentSyncManager.
         * Creates one if it doesn't exist.
         */
        fun singleton(): DocumentSyncManager = instance ?: DocumentSyncManager().also { instance = it }

        /**
         * Initializes the singleton with a custom instance.
         * Useful for testing or custom configuration.
         */
        fun initialize(manager: DocumentSyncManager) {
            instance = manager
        }
    }
}
