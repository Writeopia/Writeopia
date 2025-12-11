@file:OptIn(ExperimentalTime::class)

package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.sdk.models.utils.toBoolean
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class AccountMenuKmpViewModel(
    private val authRepository: AuthRepository,
    private val workspaceSync: WorkspaceSync,
) : AccountMenuViewModel, ViewModel() {

    private val _lastWorkspaceSync = MutableStateFlow<ResultData<String>>(ResultData.Idle())
    override val lastWorkspaceSync: StateFlow<ResultData<String>> = _lastWorkspaceSync.asStateFlow()

    override val isLoggedIn: StateFlow<ResultData<Boolean>> by lazy {
        authRepository.listenForUser().map {
            ResultData.Complete(it.id != WriteopiaUser.DISCONNECTED)
        }.stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Loading())
    }

    override fun logout(onLogOutSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            authRepository.unselectAllWorkspaces()
            val result = authRepository.logout()

            if (result.toBoolean()) {
                withContext(Dispatchers.Main) {
                    onLogOutSuccess()
                }
            }
        }
    }

    override fun syncWorkspace() {
        viewModelScope.launch(Dispatchers.Default) {
            _lastWorkspaceSync.value = ResultData.Loading()

            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
            val workspaceId = workspace.id
            val result = workspaceSync.syncWorkspace(workspaceId, force = true)

            _lastWorkspaceSync.value = if (result is ResultData.Complete) {
                val lastSync = Clock.System
                    .now()
//                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .toString()

                ResultData.Complete("Last sync: $lastSync")
            } else {
                println("result error: $result")
                ResultData.Error(RuntimeException("Error in sync"))
            }
        }
    }
}
