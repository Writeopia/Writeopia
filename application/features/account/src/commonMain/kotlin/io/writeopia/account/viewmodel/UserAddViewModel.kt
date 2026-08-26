package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserAddViewModel(
    private val workspaceId: String,
    private val workspaceName: String,
    private val userId: String,
    private val userName: String,
    private val userEmail: String,
    private val workspaceApi: WorkspaceApi,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _selectedRole = MutableStateFlow(Role.EDITOR)
    val selectedRole: StateFlow<Role> = _selectedRole.asStateFlow()

    private val _addUserState = MutableStateFlow<ResultData<Unit>>(ResultData.Idle())
    val addUserState: StateFlow<ResultData<Unit>> = _addUserState.asStateFlow()

    fun getWorkspaceName(): String = workspaceName

    fun getUserId(): String = userId

    fun getUserName(): String = userName

    fun getUserEmail(): String = userEmail

    fun selectRole(role: Role) {
        _selectedRole.value = role
    }

    fun addUser(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val token = authRepository.getAuthToken() ?: return@launch

            _addUserState.value = ResultData.Loading()

            val result = workspaceApi.addUserToWorkspaceWithRole(
                workspaceId = workspaceId,
                userEmail = userEmail,
                role = _selectedRole.value,
                token = token
            )

            _addUserState.value = result

            if (result is ResultData.Complete) {
                onSuccess()
            }
        }
    }
}
