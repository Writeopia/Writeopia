@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.di

import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.WorkspaceHandler
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class WorkspaceHandlerImpl(
    private val authRepository: AuthRepository,
    private val workspaceApi: WorkspaceApi,
    private val workspaceSync: WorkspaceSync,
    private val workspaceConfigRepository: WorkspaceConfigRepository
) : WorkspaceHandler {

    private val _availableWorkspaces: MutableStateFlow<ResultData<List<Workspace>>> =
        MutableStateFlow(ResultData.Idle())
    override val availableWorkspaces: StateFlow<ResultData<List<Workspace>>> = _availableWorkspaces

    private lateinit var coroutineScope: CoroutineScope

    override fun initScope(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    private val _selectedWorkspaceId = MutableStateFlow<String?>(null)
    override val selectedWorkspace: Flow<Workspace?> by lazy {
        combine(_availableWorkspaces, _selectedWorkspaceId) { workspacesResult, selectedId ->
            if (workspacesResult is ResultData.Complete) {
                workspacesResult.data.firstOrNull { it.id == selectedId }
            } else {
                null
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val usersOfSelectedWorkspace: Flow<ResultData<List<String>>> =
        selectedWorkspace.flatMapLatest { workspace ->
            val workspaceId = workspace?.id

            flow {
                val token = authRepository.getAuthToken()

                if (token != null && workspaceId != null) {
                    workspaceApi.getUsersOfWorkspace(workspaceId, token, forceRefresh = false)
                        .collect { emit(it) }
                } else {
                    emit(ResultData.Error())
                }
            }
        }

    private val _lastWorkspaceSync = MutableStateFlow<ResultData<String>>(ResultData.Idle())
    override val lastWorkspaceSync: StateFlow<ResultData<String>> = _lastWorkspaceSync.asStateFlow()

    private val _workspaceLocalPath = MutableStateFlow("")
    override val workspaceLocalPath: StateFlow<String> = _workspaceLocalPath.asStateFlow()

    override fun loadAvailableWorkspaces() {
        coroutineScope.launch {
            val result = authRepository.getAuthToken()?.let { token ->
                workspaceApi.getAvailableWorkspaces(token)
            }

            if (result != null) {
                _availableWorkspaces.value = result
            }
        }
    }

    override fun selectWorkspaceToManage(workspaceId: String) {
        val currentSelected = _selectedWorkspaceId.value

        if (currentSelected == workspaceId) {
            _selectedWorkspaceId.value = null
        } else {
            _selectedWorkspaceId.value = workspaceId
        }
    }

    override fun syncWorkspace() {
        coroutineScope.launch {
            _lastWorkspaceSync.value = ResultData.Loading()

            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
            val workspaceId = workspace.id
            val result = workspaceSync.syncWorkspace(workspaceId, force = true)

            _lastWorkspaceSync.value = if (result is ResultData.Complete) {
                val lastSync = Clock.System
                    .now()
                    .toString()

                ResultData.Complete("Last sync: $lastSync")
            } else {
                println("result error: $result")
                ResultData.Error(RuntimeException("Error in sync"))
            }
        }
    }

    override fun addUserToWorkspace(userEmail: String) {
        coroutineScope.launch {
            val workspaceId = _selectedWorkspaceId.value

            if (workspaceId != null) {
                authRepository.getAuthToken()?.let { token ->
                    val result = workspaceApi.addUserToWorkspace(workspaceId, userEmail, token)

                    if (result is ResultData.Complete) {
                        workspaceApi.refreshUsersInWorkspace(workspaceId, token)
                    }
                }
            }
        }
    }

    override fun changeWorkspaceLocalPath(path: String) {
        coroutineScope.launch(Dispatchers.Default) {
            val userId = authRepository.getUser().id

            workspaceConfigRepository.saveWorkspacePath(path, userId)
            _workspaceLocalPath.value = workspaceConfigRepository.loadWorkspacePath(userId) ?: ""
        }
    }

    override fun initWorkspacePath() {
        coroutineScope.launch {
            _workspaceLocalPath.value =
                workspaceConfigRepository.loadWorkspacePath(authRepository.getUser().id) ?: ""
        }
    }
}
