package io.writeopia.auth.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class ChooseWorkspaceViewModel(
    private val authRepository: AuthRepository,
    private val workspaceApi: WorkspaceApi
) : ViewModel() {

    private val _workspacesState = MutableStateFlow<ResultData<List<Workspace>>>(ResultData.Idle())
    val workspacesState: StateFlow<ResultData<List<Workspace>>> = _workspacesState.asStateFlow()

    fun loadWorkspaces() {
        viewModelScope.launch {
            val token = authRepository.getAuthToken()

            if (token != null) {
                _workspacesState.value = ResultData.Loading()
                _workspacesState.value = workspaceApi.getAvailableWorkspaces(token)
            } else {
                _workspacesState.value = ResultData.Complete(
                    listOf(Workspace.disconnectedWorkspace())
                )
            }
        }
    }

    fun chooseWorkspace(workspace: Workspace, sideEffect: () -> Unit) {
        viewModelScope.launch {
            authRepository.unselectAllWorkspaces()
            authRepository.saveWorkspace(workspace.copy(lastSync = Instant.DISTANT_PAST))
            sideEffect()
        }
    }
}
