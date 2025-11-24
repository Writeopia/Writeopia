package io.writeopia.global.shell.viewmodel

import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.OllamaRepository
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.collections.toNodeTree
import io.writeopia.common.utils.collections.traverse
import io.writeopia.common.utils.download.DownloadParser
import io.writeopia.common.utils.download.DownloadState
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.common.utils.toList
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.commonui.extensions.toUiCard
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.UiConfiguration
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.notemenu.viewmodel.FolderController
import io.writeopia.notemenu.viewmodel.FolderStateController
import io.writeopia.repository.UiConfigurationRepository
import io.writeopia.responses.DownloadModelResponse
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.utils.map
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

class GlobalShellKmpViewModel(
    private val notesUseCase: NotesUseCase,
    private val uiConfigurationRepo: UiConfigurationRepository,
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
    private val notesNavigationUseCase: NotesNavigationUseCase,
    private val folderStateController: FolderStateController =
        FolderStateController.singleton(notesUseCase, authRepository),
    private val workspaceConfigRepository: WorkspaceConfigRepository,
    private val ollamaRepository: OllamaRepository,
    private val workspaceSync: WorkspaceSync,
    private val workspaceApi: WorkspaceApi,
    private val keyboardEventFlow: Flow<KeyboardEvent>?,
) : GlobalShellViewModel, ViewModel(), FolderController by folderStateController {

    private var localUserId: String? = null
    private var sideMenuWidthState = MutableStateFlow<Float?>(null)

    private val _showSettingsState = MutableStateFlow(false)
    override val showSettingsState: StateFlow<Boolean> = _showSettingsState.asStateFlow()

    private val expandedFolders = MutableStateFlow(setOf<String>())

    private val _showSearchDialog = MutableStateFlow(false)
    override val showSearchDialog: StateFlow<Boolean> = _showSearchDialog.asStateFlow()

    private val _workspaceLocalPath = MutableStateFlow("")
    override val workspaceLocalPath: StateFlow<String> = _workspaceLocalPath.asStateFlow()

    private val retryModels = MutableStateFlow(0)

    private val loginStateTrigger = MutableStateFlow(GenerateId.generate())

    private val _lastWorkspaceSync = MutableStateFlow<ResultData<String>>(ResultData.Idle())
    override val lastWorkspaceSync: StateFlow<ResultData<String>> = _lastWorkspaceSync.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ollamaConfigState = authRepository.listenForUser().flatMapLatest { user ->
        ollamaRepository.listenForConfiguration(user.id)
    }

    override val userState: StateFlow<WriteopiaUser> = loginStateTrigger.map {
        authRepository.getUser()
    }.stateIn(viewModelScope, SharingStarted.Lazily, WriteopiaUser.disconnectedUser())

    private val _downloadModelState =
        MutableStateFlow<ResultData<DownloadModelResponse>>(ResultData.Idle())

    override val downloadModelState: StateFlow<ResultData<DownloadState>> =
        _downloadModelState.map { resultData ->
            resultData.map { response ->
                val completed = DownloadParser.toHumanReadableAmount(response.completed)
                val total = DownloadParser.toHumanReadableAmount(response.total)

                val info = buildString {
                    completed.takeIf { it.isNotEmpty() }?.let {
                        append(it)
                    }

                    total.takeIf { it.isNotEmpty() }?.let {
                        append("/$it")
                    }
                }

                DownloadState(
                    title = response.modelName ?: "",
                    info = info,
                    percentage = response.completed?.toFloat()
                        ?.div(response.total?.toFloat() ?: 1F) ?: 0F
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Idle())

    override val ollamaUrl: StateFlow<String> =
        ollamaConfigState.map { config ->
            config?.url.takeIf { it?.isNotEmpty() == true } ?: ""
        }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    override val ollamaSelectedModelState = ollamaConfigState
        .map { config -> config?.selectedModel ?: "" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    override val highlightItem: StateFlow<String?> by lazy {
        notesNavigationUseCase.navigationState
            .map { navigation -> navigation.id }
            .stateIn(viewModelScope, SharingStarted.Lazily, NotesNavigation.Root.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val modelsForUrl: StateFlow<ResultData<List<String>>> =
        combine(ollamaUrl, retryModels) { url, _ ->
            url
        }.flatMapLatest { url ->
            ollamaRepository.listenToModels(url)
        }.map { result ->
            result.map { modelResponse ->
                val models = modelResponse.models
                    .map { it.model }
                    .takeIf { it.isNotEmpty() }
                    ?: listOf("No models found")

                models
            }
        }.onEach { modelsResult ->
            if (modelsResult is ResultData.Complete && modelsResult.data.size == 1) {
                selectOllamaModel(modelsResult.data.first())
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Idle())

    override val editFolderState: StateFlow<Folder?> by lazy {
        combine(
            folderStateController.editingFolderState,
            menuItemsPerFolderId,
        ) { selectedFolder, menuItems ->
            if (selectedFolder != null) {
                menuItems[selectedFolder.parentId]
                    ?.find { menuItem ->
                        menuItem.id == selectedFolder.id
                    } as? Folder
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val uiConfiguration: Flow<UiConfiguration> by lazy {
        authRepository.listenForUser().flatMapLatest { user ->
            uiConfigurationRepo.listenForUiConfiguration(user.id, viewModelScope)
        }.filterNotNull()
    }

    override val showSideMenuState: StateFlow<Float> by lazy {
        combine(
            uiConfiguration,
            sideMenuWidthState.asStateFlow()
        ) { configuration, width ->
            width ?: configuration.sideMenuWidth
        }.stateIn(viewModelScope, SharingStarted.Lazily, 280F)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val menuItemsPerFolderId: StateFlow<Map<String, List<MenuItem>>> by lazy {
        combine(
            authRepository.listenForUser(),
            authRepository.listenForWorkspace(),
            notesNavigationUseCase.navigationState
        ) { user, workspace, notesNavigation ->
            Triple(user, notesNavigation, workspace)
        }.flatMapLatest { (user, notesNavigation, workspace) ->
            notesUseCase.listenForMenuItemsPerFolderId(notesNavigation, user.id, workspace.id)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    }

    override val sideMenuItems: StateFlow<List<MenuItemUi>> by lazy {
        combine(
            expandedFolders,
            menuItemsPerFolderId,
            highlightItem,
        ) { expanded, folderMap, highlighted ->
            val folderUiMap = folderMap.mapValues { (_, item) ->
                item.map {
                    it.toUiCard(
                        expanded = expanded.contains(it.id),
                        highlighted = it.id == highlighted
                    )
                }
            }

            val itemsList = folderUiMap
                .toNodeTree(
                    MenuItemUi.FolderUi.root(),
//                    filterPredicate = { menuItemUi ->
//                        expanded.contains(menuItemUi.documentId)
//                    }
                )
                .toList()

            itemsList.toMutableList().apply {
                removeAt(0)
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    override val folderPath: StateFlow<List<String>> by lazy {
        combine(
            menuItemsPerFolderId,
            notesNavigationUseCase.navigationState
        ) { perFolder, navigation ->
            val menuItems = perFolder.values.flatten().map { it.toUiCard() }
            listOf("Home") + menuItems.traverse(
                navigation.id,
                filterPredicate = { item -> item is MenuItemUi.FolderUi },
                mapFunc = { item -> item.title }
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    private val _showDeleteConfirmation = MutableStateFlow(false)
    override val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _availableWorkspaces: MutableStateFlow<ResultData<List<Workspace>>> =
        MutableStateFlow(ResultData.Idle())
    override val availableWorkspaces: StateFlow<ResultData<List<Workspace>>> = _availableWorkspaces

    private val _workspaceToEdit = MutableStateFlow<String?>(null)
    override val workspaceToEdit: StateFlow<Workspace?> =
        combine(_availableWorkspaces, _workspaceToEdit) { workspacesResult, selectedId ->
            if (workspacesResult is ResultData.Complete) {
                workspacesResult.data.firstOrNull { it.id == selectedId }
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val usersOfWorkspaceToEdit: StateFlow<ResultData<List<String>>> =
        workspaceToEdit.flatMapLatest { workspace ->
            val token = authRepository.getAuthToken()
            val workspaceId = workspace?.id

            if (token != null && workspaceId != null) {
                workspaceApi.getUsersOfWorkspace(workspaceId, token, forceRefresh = false)
            } else {
                flow { emit(ResultData.Error()) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ResultData.Idle())

    init {
        folderStateController.initCoroutine(viewModelScope)

        viewModelScope.launch {
            keyboardEventFlow
                ?.onEach { delay(60) }
                ?.collect { event ->
                    when (event) {
                        KeyboardEvent.SEARCH -> {
                            showSearch()
                        }

                        else -> {}
                    }
                }
        }

        viewModelScope.launch {
            val result = authRepository.getAuthToken()?.let { token ->
                workspaceApi.getAvailableWorkspaces(token)
            }

            if (result != null) {
                _availableWorkspaces.value = result
            }
        }
    }

    override fun init() {
        viewModelScope.launch {
            _workspaceLocalPath.value =
                workspaceConfigRepository.loadWorkspacePath(authRepository.getUser().id) ?: ""
        }
    }

    override fun expandFolder(id: String) {
        val expanded = expandedFolders.value
        if (expanded.contains(id)) {
            viewModelScope.launch(Dispatchers.Default) {
                expandedFolders.value = expanded - id
            }
        } else {
            viewModelScope.launch {
                val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()

                notesUseCase.listenForMenuItemsByParentId(
                    id,
                    getUserId(),
                    workspace.id
                )

                expandedFolders.value = expanded + id
            }
        }
    }

    override fun toggleSideMenu() {
        val width = showSideMenuState.value

        sideMenuWidthState.value = if (width.dp < 5.dp) 280F else 0F
        saveMenuWidth()
    }

    override fun saveMenuWidth() {
        val width = sideMenuWidthState.value ?: 280F

        viewModelScope.launch(Dispatchers.Default) {
            val uiConfiguration =
                uiConfigurationRepo.getUiConfigurationEntity(authRepository.getUser().id)
                    ?: UiConfiguration(
                        userId = getUserId(),
                        colorThemeOption = ColorThemeOption.SYSTEM,
                        sideMenuWidth = width
                    )
            uiConfigurationRepo.insertUiConfiguration(uiConfiguration.copy(sideMenuWidth = width))
        }
    }

    override fun moveSideMenu(width: Float) {
        sideMenuWidthState.value = width
    }

    override fun showSettings() {
        _showSettingsState.value = true
    }

    override fun hideSettings() {
        _showSettingsState.value = false
    }

    override fun showSearch() {
        _showSearchDialog.value = true
    }

    override fun hideSearch() {
        _showSearchDialog.value = false
    }

    override fun changeIcons(menuItemId: String, icon: String, tint: Int, iconChange: IconChange) {
        viewModelScope.launch {
            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()

            when (iconChange) {
                IconChange.FOLDER -> notesUseCase.updateFolderById(menuItemId) { folder ->
                    folder.copy(
                        icon = MenuItem.Icon(icon, tint),
                        lastUpdatedAt = Clock.System.now()
                    )
                }

                IconChange.DOCUMENT -> notesUseCase.updateDocumentById(
                    menuItemId,
                    workspace.id
                ) { document ->
                    document.copy(
                        icon = MenuItem.Icon(icon, tint),
                        lastUpdatedAt = Clock.System.now()
                    )
                }
            }
        }
    }

    override fun changeWorkspaceLocalPath(path: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val userId = authRepository.getUser().id

            workspaceConfigRepository.saveWorkspacePath(path, userId)
            _workspaceLocalPath.value = workspaceConfigRepository.loadWorkspacePath(userId) ?: ""
        }
    }

    override fun changeOllamaUrl(url: String) {
        viewModelScope.launch(Dispatchers.Default) {
            ollamaRepository.saveOllamaUrl(getUserId(), url)
        }
    }

    override fun selectOllamaModel(model: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val userId = getUserId()
            ollamaRepository.saveOllamaSelectedModel(userId, model)
            ollamaRepository.refreshConfiguration(userId)
        }
    }

    override fun retryModels() {
        retryModels.value = Random.nextInt()
    }

    override fun modelToDownload(model: String, onComplete: () -> Unit) {
        if (model.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val url = ollamaRepository.getConfiguredUrl(getUserId())?.trim()

            if (url != null) {
                ollamaRepository.downloadModel(model, url)
                    .onCompletion {
                        retryModels()
                        onComplete()
                    }
                    .collectLatest { result ->
                        _downloadModelState.value = result

                        val modelsResult = ollamaRepository.getModels(url)

                        if (
                            modelsResult is ResultData.Complete &&
                            modelsResult.data.models.size == 1
                        ) {
                            val userId = authRepository.getUser().id

                            ollamaRepository.saveOllamaSelectedModel(
                                userId,
                                modelsResult.data.models.first().model
                            )
                            ollamaRepository.refreshConfiguration(userId)
                        }
                    }
            }
        }
    }

    override fun deleteModel(model: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val url = ollamaRepository.getConfiguredUrl(getUserId())?.trim()
            println("deleteModel. url: $url")

            if (url != null) {
                ollamaRepository.deleteModel(model, url)

                retryModels()
            }
        }
    }

    override fun logout(sideEffect: () -> Unit) {
        viewModelScope.launch {
            val currentUserId = authRepository.getUser().id

            authRepository.unselectAllWorkspaces()
            authRepository.logout()
            authRepository.saveToken(currentUserId, "")

//            AppConnectionInjection.singleton().setJwtToken("")
            loginStateTrigger.value = GenerateId.generate()
            sideEffect()
        }
    }

    override fun deleteAccount(sideEffect: () -> Unit) {
        viewModelScope.launch {
            val id = authRepository.getUser().id

            if (id != WriteopiaUser.DISCONNECTED) {
                val result = authRepository.getAuthToken()?.let { token ->
                    authApi.deleteAccount(token)
                }

                if (result is ResultData.Complete && result.data) {
                    authRepository.unselectAllWorkspaces()
                    authRepository.logout()
                    loginStateTrigger.value = GenerateId.generate()
                    dismissDeleteConfirm()
                    logout(sideEffect = sideEffect)
                }
            }
        }
    }

    override fun dismissDeleteConfirm() {
        _showDeleteConfirmation.value = false
    }

    override fun showDeleteConfirm() {
        _showDeleteConfirmation.value = true
    }

    override fun syncWorkspace() {
        viewModelScope.launch {
            _lastWorkspaceSync.value = ResultData.Loading()

            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
            val workspaceId = workspace.id
            val result = workspaceSync.syncWorkspace(workspaceId, force = true)

            _lastWorkspaceSync.value = if (result is ResultData.Complete) {
                val lastSync = Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .toString()

                ResultData.Complete("Last sync: $lastSync")
            } else {
                println("result error: $result")
                ResultData.Error(RuntimeException("Error in sync"))
            }
        }
    }

    override fun addUserToTeam(userEmail: String) {
        viewModelScope.launch {
            val workspace = workspaceToEdit.value

            if (workspace != null) {
                authRepository.getAuthToken()?.let { token ->
                    val result = workspaceApi.addUserToWorkspace(workspace.id, userEmail, token)

                    if (result is ResultData.Complete) {
                        workspaceApi.refreshUsersInWorkspace(workspace.id, token)
                    }
                }
            }
        }
    }

    override fun selectWorkspaceToManage(workspaceId: String) {
        _workspaceToEdit.value = workspaceId
    }

    private suspend fun getUserId(): String =
        localUserId ?: authRepository.getUser().id.also { id ->
            localUserId = id
        }
}
