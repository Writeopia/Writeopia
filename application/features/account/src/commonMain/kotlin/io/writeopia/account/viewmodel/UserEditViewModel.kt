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

class UserEditViewModel(
    private val workspaceId: String,
    private val workspaceName: String,
    private val userId: String,
    private val userName: String,
    private val userEmail: String,
    private val currentRole: String,
    private val workspaceApi: WorkspaceApi,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _selectedRole = MutableStateFlow(Role.fromString(currentRole))
    val selectedRole: StateFlow<Role> = _selectedRole.asStateFlow()

    private val _updateUserState = MutableStateFlow<ResultData<Unit>>(ResultData.Idle())
    val updateUserState: StateFlow<ResultData<Unit>> = _updateUserState.asStateFlow()

    fun getWorkspaceName(): String = workspaceName

    fun getUserId(): String = userId

    fun getUserName(): String = userName

    fun getUserEmail(): String = userEmail

    fun getCurrentRole(): String = currentRole

    fun selectRole(role: Role) {
        _selectedRole.value = role
    }

    fun hasRoleChanged(): Boolean = _selectedRole.value.value != currentRole

    fun updateUserRole(onSuccess: () -> Unit) {
        if (!hasRoleChanged()) {
            onSuccess()
            return
        }

        viewModelScope.launch {
            _updateUserState.value = ResultData.Loading()

            val result = workspaceApi.changeUserRole(
                workspaceId = workspaceId,
                userId = userId,
                newRole = _selectedRole.value
            )

            _updateUserState.value = result

            if (result is ResultData.Complete) {
                onSuccess()
            }
        }
    }
}
