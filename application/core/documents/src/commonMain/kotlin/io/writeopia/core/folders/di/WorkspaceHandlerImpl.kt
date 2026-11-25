@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.di

import io.writeopia.app.dto.SearchUserApi
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.WorkspaceHandler
import io.writeopia.core.folders.sync.ConfigFileWatcher
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.utils.map
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val workspaceConfigRepository: WorkspaceConfigRepository,
    private val configFileWatcher: ConfigFileWatcher
) : WorkspaceHandler {

    private val _availableWorkspaces: MutableStateFlow<ResultData<List<Workspace>>> =
        MutableStateFlow(ResultData.Idle())
    override val availableWorkspaces: StateFlow<ResultData<List<Workspace>>> = _availableWorkspaces

    private lateinit var coroutineScope: CoroutineScope

    private val _userSearchQuery = MutableStateFlow("")
    override val userSearchQuery: StateFlow<String> = _userSearchQuery.asStateFlow()

    private val _userSearchResults = MutableStateFlow<ResultData<List<SearchUserApi>>>(ResultData.Idle())
    override val userSearchResults: StateFlow<ResultData<List<SearchUserApi>>> = _userSearchResults.asStateFlow()

    private var shouldSearchUsers = true

    override fun initScope(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope

        // Collect config file changes and trigger local sync when lastUpdateTable increases
        coroutineScope.launch {
            configFileWatcher.configChanges.collect { newTimestamp ->
                if (newTimestamp != null) {
                    _localSyncRequired.emit(Unit)
                }
            }
        }

        coroutineScope.launch {
            _userSearchQuery.collectLatest { rawQuery ->
                if (!shouldSearchUsers) return@collectLatest
                val workspaceId = selectedWorkspaceId() ?: return@collectLatest
                val query = rawQuery.trim().removePrefix("@")
                if (query.length < 2) {
                    _userSearchResults.value = ResultData.Idle()
                    return@collectLatest
                }

                delay(300)
                _userSearchResults.value = ResultData.Loading()
                _userSearchResults.value = workspaceApi.searchUsers(
                    workspaceId = workspaceId,
                    usernameQuery = query,
                    pageSize = 5
                ).map { it.users }
            }
        }
    }

    private val _selectedWorkspaceId = MutableStateFlow<String?>(null)

    /**
     * The workspace being managed. Until the user picks one, the first available workspace is
     * selected, so the management screen never opens without a workspace.
     */
    override val selectedWorkspace: Flow<Workspace?> by lazy {
        combine(_availableWorkspaces, _selectedWorkspaceId) { workspacesResult, selectedId ->
            selectedWorkspaceOrFirst(workspacesResult, selectedId)
        }
    }

    private fun selectedWorkspaceOrFirst(
        workspacesResult: ResultData<List<Workspace>>,
        selectedId: String?
    ): Workspace? {
        if (workspacesResult !is ResultData.Complete) return null
        val workspaces = workspacesResult.data

        return workspaces.firstOrNull { it.id == selectedId } ?: workspaces.firstOrNull()
    }

    private fun selectedWorkspaceId(): String? =
        selectedWorkspaceOrFirst(_availableWorkspaces.value, _selectedWorkspaceId.value)?.id

    @OptIn(ExperimentalCoroutinesApi::class)
    override val usersOfSelectedWorkspace: Flow<ResultData<List<String>>> =
        selectedWorkspace.flatMapLatest { workspace ->
            val workspaceId = workspace?.id

            flow {
                if (workspaceId != null) {
                    workspaceApi.getUsersOfWorkspace(workspaceId, forceRefresh = false)
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

    private val _isAutoSyncEnabled = MutableStateFlow(false)
    override val isAutoSyncEnabled: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    private val _localSyncRequired = MutableSharedFlow<Unit>(replay = 0)
    override val localSyncRequired: Flow<Unit> = _localSyncRequired.asSharedFlow()

    private val _exportWorkspaceState = MutableStateFlow<ResultData<Unit>>(ResultData.Idle())
    override val exportWorkspaceState: StateFlow<ResultData<Unit>> = _exportWorkspaceState.asStateFlow()

    override fun loadAvailableWorkspaces() {
        // A reload keeps showing the workspaces already loaded instead of a loading state.
        if (_availableWorkspaces.value !is ResultData.Complete) {
            _availableWorkspaces.value = ResultData.Loading()
        }

        coroutineScope.launch {
            // The private space has no account, so there are no cloud workspaces to list and the
            // backend isn't contacted at all.
            _availableWorkspaces.value = if (authRepository.isLoggedIn()) {
                workspaceApi.getAvailableWorkspaces()
            } else {
                ResultData.Complete(emptyList())
            }
        }
    }

    override fun selectWorkspaceToManage(workspaceId: String) {
        shouldSearchUsers = false
        _userSearchQuery.value = ""
        _userSearchResults.value = ResultData.Idle()

        _selectedWorkspaceId.value = workspaceId
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

    override fun addUserToWorkspace(username: String) {
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanUsername.isBlank()) return

        shouldSearchUsers = false
        _userSearchQuery.value = ""
        _userSearchResults.value = ResultData.Idle()

        coroutineScope.launch {
            val workspaceId = selectedWorkspaceId()

            if (workspaceId != null) {
                val result = workspaceApi.addUserToWorkspace(workspaceId, cleanUsername)

                if (result is ResultData.Complete) {
                    workspaceApi.refreshUsersInWorkspace(workspaceId)
                }
            }
        }
    }

    override fun onUserSearchQueryChange(query: String) {
        shouldSearchUsers = true
        _userSearchQuery.value = query
        if (query.trim().removePrefix("@").length < 2) {
            _userSearchResults.value = ResultData.Idle()
        }
    }

    override fun onUserSuggestionSelect(user: SearchUserApi) {
        shouldSearchUsers = false
        _userSearchQuery.value = user.username
        _userSearchResults.value = ResultData.Idle()
    }

    override fun changeWorkspaceLocalPath(path: String) {
        coroutineScope.launch(Dispatchers.Default) {
            val userId = authRepository.getUser().id
            val wasAutoSyncEnabled = _isAutoSyncEnabled.value

            // Stop watching old path
            if (wasAutoSyncEnabled) {
                stopAutoSync()
            }

            workspaceConfigRepository.saveWorkspacePath(path, userId)
            _workspaceLocalPath.value = workspaceConfigRepository.loadWorkspacePath(userId) ?: ""

            // Restart watching new path if auto-sync was enabled
            if (wasAutoSyncEnabled && _workspaceLocalPath.value.isNotBlank()) {
                startAutoSync()
            }
        }
    }

    override fun initWorkspacePath() {
        coroutineScope.launch {
            val path = workspaceConfigRepository.loadWorkspacePath(authRepository.getUser().id) ?: ""
            _workspaceLocalPath.value = path

            // Auto-start watching if a path is configured
            if (path.isNotBlank()) {
                startAutoSync()
            }
        }
    }

    override fun startAutoSync() {
        val path = _workspaceLocalPath.value
        if (path.isNotBlank()) {
            configFileWatcher.startWatching(path)
            _isAutoSyncEnabled.value = configFileWatcher.isWatching
        }
    }

    override fun stopAutoSync() {
        configFileWatcher.stopWatching()
        _isAutoSyncEnabled.value = false
    }

    override fun exportWorkspace(workspaceId: String) {
        coroutineScope.launch {
            _exportWorkspaceState.value = ResultData.Loading()
            val result = workspaceApi.exportWorkspace(workspaceId)
            _exportWorkspaceState.value = result
        }
    }

    override fun resetExportState() {
        _exportWorkspaceState.value = ResultData.Idle()
    }

    override fun clearWorkspaces() {
        // Called on logout: without an account there are no cloud workspaces, so the list is
        // known to be empty (the offline workspace) rather than not loaded yet.
        _availableWorkspaces.value = ResultData.Complete(emptyList())
        _selectedWorkspaceId.value = null
        shouldSearchUsers = false
        _userSearchQuery.value = ""
        _userSearchResults.value = ResultData.Idle()
    }
}
