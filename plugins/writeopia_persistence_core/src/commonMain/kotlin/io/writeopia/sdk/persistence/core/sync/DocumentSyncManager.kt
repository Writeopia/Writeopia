package io.writeopia.sdk.persistence.core.sync

import io.writeopia.sdk.manager.DocumentTracker
import io.writeopia.sdk.manager.StoryStepSyncTracker
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.persistence.core.tracker.OnUpdateStoryStepSyncTracker
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
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
    private val activeBackendSyncJobs = mutableMapOf<String, Job>()

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
     * Registers a document for backend syncing.
     *
     * This syncs document changes to the backend in real-time with debouncing.
     * Changes are buffered and sent every 500ms (configurable) to avoid excessive network requests.
     *
     * @param documentId The unique identifier of the document to sync
     * @param documentEditionFlow Flow emitting the document state and info on each change
     * @param workspaceIdFlow Flow emitting the current workspace ID
     * @param syncApi Function that performs the actual sync with the backend (request, token) -> response
     * @param tokenProvider Function that provides the current authentication token
     * @param onServerUpdate Callback invoked when server updates should be applied locally
     */
    fun registerForBackendSync(
        documentId: String,
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
        workspaceIdFlow: Flow<String>,
        syncApi: suspend (StoryStepSyncRequest, String) -> StoryStepSyncResponse,
        tokenProvider: suspend () -> String?,
        onServerUpdate: suspend (List<Pair<Double, StoryStep>>, List<String>) -> Unit = { _, _ -> }
    ) {
        // Cancel any existing backend sync for this document
        activeBackendSyncJobs[documentId]?.cancel()

        val storyStepSyncTracker: StoryStepSyncTracker = OnUpdateStoryStepSyncTracker(
            syncApi = syncApi,
            tokenProvider = tokenProvider,
            onServerUpdate = onServerUpdate
        )

        // Start a new backend sync job in the global scope
        val job = scope.launch(dispatcher) {
            storyStepSyncTracker.syncStorySteps(
                documentEditionFlow,
                workspaceIdFlow
            )
        }

        activeBackendSyncJobs[documentId] = job
    }

    /**
     * Unregisters a document from syncing.
     *
     * This will cancel both the local and backend sync jobs for the specified document.
     * Note: Any pending sync operations may not complete if cancelled.
     *
     * @param documentId The unique identifier of the document to stop syncing
     */
    fun unregisterFromSync(documentId: String) {
        activeSyncJobs[documentId]?.cancel()
        activeSyncJobs.remove(documentId)
        activeBackendSyncJobs[documentId]?.cancel()
        activeBackendSyncJobs.remove(documentId)
    }

    /**
     * Checks if a document is currently registered for syncing (local or backend).
     *
     * @param documentId The unique identifier of the document
     * @return true if the document is currently being synced
     */
    fun isSyncing(documentId: String): Boolean {
        val localJob = activeSyncJobs[documentId]
        val backendJob = activeBackendSyncJobs[documentId]
        return (localJob != null && localJob.isActive) ||
            (backendJob != null && backendJob.isActive)
    }

    /**
     * Checks if a document is currently syncing to the backend.
     *
     * @param documentId The unique identifier of the document
     * @return true if the document is currently syncing to the backend
     */
    fun isBackendSyncing(documentId: String): Boolean {
        val job = activeBackendSyncJobs[documentId]
        return job != null && job.isActive
    }

    /**
     * Returns the number of documents currently being synced (local + backend).
     */
    fun activeSyncCount(): Int =
        activeSyncJobs.values.count { it.isActive } +
            activeBackendSyncJobs.values.count { it.isActive }

    /**
     * Cancels all active sync jobs (both local and backend).
     *
     * Use with caution - this may result in data loss if there are pending changes.
     */
    fun cancelAllSync() {
        activeSyncJobs.values.forEach { it.cancel() }
        activeSyncJobs.clear()
        activeBackendSyncJobs.values.forEach { it.cancel() }
        activeBackendSyncJobs.clear()
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
