package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.app.dto.WorkspaceUserApi
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkspaceUsersViewModel(
    private val workspaceId: String,
    private val workspaceName: String,
    private val workspaceApi: WorkspaceApi,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _users = MutableStateFlow<ResultData<List<WorkspaceUserApi>>>(ResultData.Idle())
    val users: StateFlow<ResultData<List<WorkspaceUserApi>>> = _users.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    private val _addUserState = MutableStateFlow<ResultData<Unit>>(ResultData.Idle())
    val addUserState: StateFlow<ResultData<Unit>> = _addUserState.asStateFlow()

    private var currentPage = 1
    private val pageSize = 20
    private val loadedUsers = mutableListOf<WorkspaceUserApi>()

    fun getWorkspaceName(): String = workspaceName

    fun loadUsers() {
        viewModelScope.launch {
            val token = authRepository.getAuthToken() ?: return@launch

            _users.value = ResultData.Loading()
            currentPage = 1
            loadedUsers.clear()

            val result = workspaceApi.getUsersOfWorkspacePaginated(
                workspaceId = workspaceId,
                page = currentPage,
                pageSize = pageSize,
                token = token
            )

            when (result) {
                is ResultData.Complete -> {
                    loadedUsers.addAll(result.data.users)
                    _users.value = ResultData.Complete(loadedUsers.toList())
                    _hasMorePages.value = result.data.hasNextPage
                }
                is ResultData.Error -> {
                    _users.value = ResultData.Error(result.exception)
                }
                else -> {}
            }
        }
    }

    fun loadMoreUsers() {
        if (_isLoadingMore.value || !_hasMorePages.value) return

        viewModelScope.launch {
            val token = authRepository.getAuthToken() ?: return@launch

            _isLoadingMore.value = true
            currentPage++

            val result = workspaceApi.getUsersOfWorkspacePaginated(
                workspaceId = workspaceId,
                page = currentPage,
                pageSize = pageSize,
                token = token
            )

            when (result) {
                is ResultData.Complete -> {
                    loadedUsers.addAll(result.data.users)
                    _users.value = ResultData.Complete(loadedUsers.toList())
                    _hasMorePages.value = result.data.hasNextPage
                }
                is ResultData.Error -> {
                    currentPage-- // Revert page on error
                }
                else -> {}
            }

            _isLoadingMore.value = false
        }
    }

    fun addUser(email: String) {
        viewModelScope.launch {
            val token = authRepository.getAuthToken() ?: return@launch

            _addUserState.value = ResultData.Loading()

            val result = workspaceApi.addUserToWorkspace(
                workspaceId = workspaceId,
                userEmail = email,
                token = token
            )

            _addUserState.value = result

            if (result is ResultData.Complete) {
                // Reload users to show the newly added user
                loadUsers()
            }
        }
    }

    fun retry() {
        loadUsers()
    }

    fun resetAddUserState() {
        _addUserState.value = ResultData.Idle()
    }
}
