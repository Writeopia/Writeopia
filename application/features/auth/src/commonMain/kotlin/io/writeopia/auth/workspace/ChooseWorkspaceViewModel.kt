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

class ChooseWorkspaceViewModel(
    private val authRepository: AuthRepository,
    private val workspaceApi: WorkspaceApi
) : ViewModel() {

    private val _workspacesState = MutableStateFlow<ResultData<List<Workspace>>>(ResultData.Idle())
    val workspacesState: StateFlow<ResultData<List<Workspace>>> = _workspacesState.asStateFlow()

    fun loadWorkspaces() {
        viewModelScope.launch {
            authRepository.getAuthToken()?.let { token ->
                _workspacesState.value = workspaceApi.getAvailableWorkspaces(token)
            }
        }
    }
}
