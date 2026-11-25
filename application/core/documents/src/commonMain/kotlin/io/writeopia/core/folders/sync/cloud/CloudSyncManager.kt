@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync.cloud

import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What the screens show of the cloud sync. */
data class CloudSyncStatus(
    /** This platform can sync with the user's own cloud storage. */
    val available: Boolean,
    val providerName: String = "",
    val connected: Boolean = false,
    val syncing: Boolean = false,
    val lastSync: Instant? = null,
    /** The last sync failed with this message; null after one that worked. */
    val error: String? = null,
    /** The access was taken back or expired: the user must connect again. */
    val needsReconnect: Boolean = false,
)

/**
 * Runs the sync of the offline workspace with the user's own cloud storage: when it starts, every
 * [interval] after that, and when asked ([requestSync], e.g. after a change or from the sync
 * button). Only the offline workspace syncs this way; a Writeopia account syncs with the backend.
 *
 * @param connector null when this platform has no cloud storage to offer.
 * @param currentWorkspaceId the workspace open now.
 */
class CloudSyncManager(
    private val connector: CloudAccountConnector?,
    private val cloudSync: () -> CloudWorkspaceSync,
    private val stateStore: CloudSyncStateStore,
    private val currentWorkspaceId: suspend () -> String,
    private val interval: Duration = 5.minutes,
) {
    private val _status = MutableStateFlow(
        CloudSyncStatus(available = connector != null, providerName = connector?.providerName.orEmpty())
    )
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null

    /** Starts syncing in [scope], once; later calls do nothing while it runs. */
    fun start(scope: CoroutineScope) {
        if (connector == null || loop?.isActive == true) return

        // Off the main thread: the sync reads and writes the database.
        loop = scope.launch(Dispatchers.Default) {
            _status.update { it.copy(connected = connector.isConnected()) }
            while (true) {
                syncIfActive()
                withTimeoutOrNull(interval) { requests.receive() }
            }
        }
    }

    /** A sync soon, e.g. after a change; several requests close together make one sync. */
    fun requestSync() {
        requests.trySend(Unit)
    }

    /**
     * Is the offline workspace syncing with the user's storage? While it is, deletions must stay
     * soft until the sync has recorded them, or other devices would bring them back.
     */
    suspend fun isActive(): Boolean =
        connector != null &&
            currentWorkspaceId() == Workspace.disconnectedWorkspace().id &&
            connector.isConnected()

    /** Asks the user to connect their account, then syncs. */
    suspend fun connect(): Result<Unit> {
        val connector = connector ?: return Result.failure(IllegalStateException("No cloud storage here"))
        val result = connector.connect()
        if (result.isSuccess) {
            _status.update { it.copy(connected = true, error = null, needsReconnect = false) }
            requestSync()
        } else {
            _status.update { it.copy(error = result.exceptionOrNull()?.message) }
        }
        return result
    }

    /**
     * Stops syncing and forgets the account. Nothing is deleted, here or in the storage; a later
     * connection takes every file in again.
     */
    suspend fun disconnect() {
        val connector = connector ?: return
        connector.disconnect()
        stateStore.clearSeenVersions()
        _status.update { it.copy(connected = false, error = null, needsReconnect = false, lastSync = null) }
    }

    /** Syncs now, waiting for it. Does nothing unless [isActive]. */
    suspend fun syncNow(): ResultData<Unit> {
        if (!isActive()) return ResultData.Idle()

        _status.update { it.copy(syncing = true) }
        val result = cloudSync().sync(Workspace.disconnectedWorkspace().id)
        val error = (result as? ResultData.Error)?.exception
        val unauthorized = (error as? CloudStoreException)?.unauthorized == true

        _status.update { status ->
            if (error == null) {
                status.copy(syncing = false, lastSync = Clock.System.now(), error = null, needsReconnect = false)
            } else {
                status.copy(
                    syncing = false,
                    error = error.message,
                    needsReconnect = unauthorized && connector?.accessToken(forceRefresh = false) == null,
                )
            }
        }
        return result
    }

    private suspend fun syncIfActive() {
        if (isActive()) syncNow()
    }
}
