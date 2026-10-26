package io.writeopia.auth.workspace

import androidx.lifecycle.ViewModel
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the sync screen shows after the login. */
enum class WorkspaceSyncState {
    SYNCING,
    DONE,
    FAILED
}

/**
 * Brings the whole workspace to this device right after the login, before the documents are
 * shown. The sync runs on [syncScope], not on the view model's: the person may move on to the
 * documents while it runs, and it goes on in the background.
 */
class WorkspaceSyncViewModel(
    private val authRepository: AuthRepository,
    private val workspaceSync: WorkspaceSync,
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {

    private val _state = MutableStateFlow(WorkspaceSyncState.SYNCING)
    val state: StateFlow<WorkspaceSyncState> = _state.asStateFlow()

    private var syncJob: Job? = null

    /**
     * Starts the sync, once: asked again while it runs (the screen recomposed), nothing
     * happens. A workspace with nothing to sync (offline, or not premium) is done at once.
     */
    fun startSync() {
        if (syncJob?.isActive == true) return

        syncJob = syncScope.launch {
            // The scope outlives the screen and has no handler: whatever fails here, even
            // before the sync (reading the workspace or the user), must end as FAILED rather
            // than kill the job and leave the screen syncing forever.
            _state.value = try {
                sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error syncing the workspace after login: ${e.message}")
                WorkspaceSyncState.FAILED
            }
        }
    }

    private suspend fun sync(): WorkspaceSyncState {
        val workspace = authRepository.getWorkspace()

        if (workspace == null || !syncsWithCloud(workspace)) return WorkspaceSyncState.DONE

        return when (workspaceSync.syncWorkspace(workspace.id, force = true)) {
            is ResultData.Error -> WorkspaceSyncState.FAILED
            else -> WorkspaceSyncState.DONE
        }
    }

    private suspend fun syncsWithCloud(workspace: Workspace): Boolean =
        workspace.id != Workspace.disconnectedWorkspace().id &&
            authRepository.isLoggedIn() &&
            authRepository.getUser().tier == Tier.PREMIUM
}
