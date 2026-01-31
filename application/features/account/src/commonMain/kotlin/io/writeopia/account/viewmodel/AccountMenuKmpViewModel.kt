package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.WorkspaceHandler
import io.writeopia.sdk.models.utils.toBoolean
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AccountMenuKmpViewModel(
    private val authRepository: AuthRepository,
    private val workspaceHandler: WorkspaceHandler,
) : AccountMenuViewModel, ViewModel() {

    override val lastWorkspaceSync: StateFlow<ResultData<String>> = workspaceHandler.lastWorkspaceSync

    override val availableWorkspaces: StateFlow<ResultData<List<Workspace>>> =
        workspaceHandler.availableWorkspaces

    override val selectedWorkspace: Flow<Workspace?> = workspaceHandler.selectedWorkspace

    override val usersOfSelectedWorkspace: Flow<ResultData<List<String>>> =
        workspaceHandler.usersOfSelectedWorkspace

    override val isLoggedIn: StateFlow<ResultData<Boolean>> by lazy {
        authRepository.listenForUser().map {
            ResultData.Complete(it.id != WriteopiaUser.DISCONNECTED)
        }.stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Loading())
    }

    init {
        workspaceHandler.initScope(viewModelScope)
        workspaceHandler.loadAvailableWorkspaces()
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
        workspaceHandler.syncWorkspace()
    }

    override fun selectWorkspace(workspaceId: String) {
        workspaceHandler.selectWorkspaceToManage(workspaceId)
    }

    override fun addUserToWorkspace(userEmail: String) {
        workspaceHandler.addUserToWorkspace(userEmail)
    }
}
