package io.writeopia.auth.spacechoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocalFolderSetupViewModel(
    private val authRepository: AuthRepository,
    private val workspaceConfigRepository: WorkspaceConfigRepository,
) : ViewModel() {

    private val _workspaceLocalPath = MutableStateFlow("")
    val workspaceLocalPath: StateFlow<String> = _workspaceLocalPath.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val userId = authRepository.getUser().id
            _workspaceLocalPath.value = workspaceConfigRepository.loadWorkspacePath(userId) ?: ""
        }
    }

    fun changeWorkspaceLocalPath(path: String) {
        // Updated right away so the text field doesn't lag behind typing; persisted below.
        _workspaceLocalPath.value = path

        viewModelScope.launch(Dispatchers.Default) {
            workspaceConfigRepository.saveWorkspacePath(path, authRepository.getUser().id)
        }
    }
}
