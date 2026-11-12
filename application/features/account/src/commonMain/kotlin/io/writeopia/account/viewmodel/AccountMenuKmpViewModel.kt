package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.WorkspaceHandler
import io.writeopia.sdk.models.utils.toBoolean
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.models.user.WriteopiaUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AccountMenuKmpViewModel(
    private val authRepository: AuthRepository,
    private val workspaceHandler: WorkspaceHandler,
    private val authApi: AuthApi,
) : AccountMenuViewModel, ViewModel() {

    override val lastWorkspaceSync: StateFlow<ResultData<String>> = workspaceHandler.lastWorkspaceSync

    override val availableWorkspaces: StateFlow<ResultData<List<Workspace>>> =
        workspaceHandler.availableWorkspaces

    override val selectedWorkspace: Flow<Workspace?> = workspaceHandler.selectedWorkspace

    override val usersOfSelectedWorkspace: Flow<ResultData<List<String>>> =
        workspaceHandler.usersOfSelectedWorkspace

    override val exportWorkspaceState: StateFlow<ResultData<Unit>> =
        workspaceHandler.exportWorkspaceState

    override val isLoggedIn: StateFlow<ResultData<Boolean>> by lazy {
        authRepository.listenForUser().map {
            ResultData.Complete(it.isOnline)
        }.stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Loading())
    }

    private val _logoutInProgress = MutableStateFlow(false)
    override val logoutInProgress: StateFlow<Boolean> = _logoutInProgress.asStateFlow()

    private val _deleteAccountInProgress = MutableStateFlow(false)
    override val deleteAccountInProgress: StateFlow<Boolean> =
        _deleteAccountInProgress.asStateFlow()

    private val _deleteAccountFailed = MutableStateFlow(false)
    override val deleteAccountFailed: StateFlow<Boolean> = _deleteAccountFailed.asStateFlow()

    override val userEmail: StateFlow<String> by lazy {
        authRepository.listenForUser().map { it.email }
            .stateIn(viewModelScope, SharingStarted.Lazily, "")
    }

    init {
        workspaceHandler.initScope(viewModelScope)
        workspaceHandler.loadAvailableWorkspaces()
    }

    override fun logout(onLogOutSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            _logoutInProgress.value = true
            try {
                authRepository.unselectAllWorkspaces()
                val result = authRepository.logout()

                if (result.toBoolean()) {
                    withContext(Dispatchers.Main) {
                        onLogOutSuccess()
                    }
                }
            } finally {
                _logoutInProgress.value = false
            }
        }
    }

    override fun deleteAccount(onSuccess: () -> Unit) {
        // Taken before launching, so two quick taps can't start two requests.
        if (!_deleteAccountInProgress.compareAndSet(expect = false, update = true)) return

        _deleteAccountFailed.value = false

        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (authRepository.getUser().id == WriteopiaUser.DISCONNECTED) return@launch

                // Captured before the cleanup, to revoke it afterwards.
                val refreshToken = authRepository.getRefreshToken()
                val result = authApi.deleteAccount()

                if (result is ResultData.Complete && result.data) {
                    refreshToken?.let { authApi.logout(it) }

                    authRepository.unselectAllWorkspaces()
                    authRepository.clearTokens()
                    authRepository.logout()

                    // Caches an API instance built with the old HttpClient.
                    WriteopiaConnectionInjector.clearInstance()

                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    _deleteAccountFailed.value = true
                }
            } finally {
                _deleteAccountInProgress.value = false
            }
        }
    }

    override fun changeWorkspace(onChangeSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            authRepository.unselectAllWorkspaces()

            withContext(Dispatchers.Main) {
                onChangeSuccess()
            }
        }
    }

    override fun syncWorkspace() {
        workspaceHandler.syncWorkspace()
    }

    override fun selectWorkspace(workspaceId: String) {
        workspaceHandler.selectWorkspaceToManage(workspaceId)
    }

    override fun addUserToWorkspace(username: String) {
        workspaceHandler.addUserToWorkspace(username)
    }

    override fun exportWorkspace(workspaceId: String) {
        workspaceHandler.exportWorkspace(workspaceId)
    }

    override fun resetExportState() {
        workspaceHandler.resetExportState()
    }
}
