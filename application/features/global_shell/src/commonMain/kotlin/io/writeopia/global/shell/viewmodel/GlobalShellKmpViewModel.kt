package io.writeopia.global.shell.viewmodel

import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.OllamaRepository
import io.writeopia.api.OllamaApi
import io.writeopia.auth.core.manager.AuthManager
import io.writeopia.common.utils.DISCONNECTED_USER_ID
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.common.utils.collections.traverse
import io.writeopia.common.utils.collections.toNodeTree
import io.writeopia.common.utils.download.DownloadParser
import io.writeopia.common.utils.download.DownloadState
import io.writeopia.common.utils.map
import io.writeopia.models.configuration.WorkspaceConfigRepository
import io.writeopia.common.utils.toList
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.UiConfiguration
import io.writeopia.sdk.models.document.Folder
import io.writeopia.notemenu.data.model.NotesNavigation
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.notemenu.data.usecase.NotesUseCase
import io.writeopia.commonui.extensions.toUiCard
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.notemenu.viewmodel.FolderController
import io.writeopia.notemenu.viewmodel.FolderStateController
import io.writeopia.repository.UiConfigurationRepository
import io.writeopia.responses.DownloadModelResponse
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.tutorials.Tutorials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.random.Random

class GlobalShellKmpViewModel(
    private val notesUseCase: NotesUseCase,
    private val uiConfigurationRepo: UiConfigurationRepository,
    private val authManager: AuthManager,
    private val notesNavigationUseCase: NotesNavigationUseCase,
    private val folderStateController: FolderStateController = FolderStateController(
        notesUseCase,
        authManager
    ),
    private val workspaceConfigRepository: WorkspaceConfigRepository,
    private val ollamaRepository: OllamaRepository,
    private val configRepository: ConfigurationRepository,
    private val json: Json = writeopiaJson
) : GlobalShellViewModel, ViewModel(), FolderController by folderStateController {

    init {
        folderStateController.initCoroutine(viewModelScope)

        viewModelScope.launch(Dispatchers.Default) {
            val userId = getUserId()

            if (!configRepository.hasFirstConfiguration(userId)) {
                Tutorials.allTutorialsDocuments()
                    .map { documentAsJson ->
                        json.decodeFromString<DocumentApi>(documentAsJson).toModel()
                    }
                    .forEach { document ->
                        val now = Clock.System.now()

                        notesUseCase.saveDocument(
                            document.copy(
                                createdAt = now,
                                lastUpdatedAt = now
                            )
                        )
                    }

                ollamaRepository.saveOllamaUrl(userId, OllamaApi.defaultUrl())

                configRepository.setTutorialNotes(true, userId)
            }

            ollamaRepository.refreshConfiguration(userId)
        }
    }

    private var localUserId: String? = null
    private var sideMenuWidthState = MutableStateFlow<Float?>(null)

    private val _showSettingsState = MutableStateFlow(false)
    override val showSettingsState: StateFlow<Boolean> = _showSettingsState.asStateFlow()

    private val _expandedFolders = MutableStateFlow(setOf<String>())

    private val _showSearch = MutableStateFlow(false)
    override val showSearchDialog: StateFlow<Boolean> = _showSearch.asStateFlow()

    private val _workspaceLocalPath = MutableStateFlow("")
    override val workspaceLocalPath: StateFlow<String> = _workspaceLocalPath.asStateFlow()

    private val _retryModels = MutableStateFlow(0)

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
        ollamaRepository.listenForConfiguration("disconnected_user")
            .map { config ->
                config?.url.takeIf { it?.isNotEmpty() == true } ?: ""
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    override val ollamaSelectedModelState = ollamaRepository
        .listenForConfiguration("disconnected_user")
        .map { config -> config?.selectedModel ?: "" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    override val highlightItem: StateFlow<String?> by lazy {
        notesNavigationUseCase.navigationState
            .map { navigation -> navigation.id }
            .stateIn(viewModelScope, SharingStarted.Lazily, NotesNavigation.Root.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val modelsForUrl: StateFlow<ResultData<List<String>>> =
        combine(ollamaUrl, _retryModels) { url, _ ->
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
            menuItemsPerFolderId
        ) { selectedFolder, menuItems ->
            if (selectedFolder != null) {
                val folder = menuItems[selectedFolder.parentId]?.find { menuItem ->
                    menuItem.id == selectedFolder.id
                } as? Folder

                folder
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    override val showSideMenuState: StateFlow<Float> by lazy {
        combine(
            uiConfigurationRepo.listenForUiConfiguration(::getUserId, viewModelScope)
                .filterNotNull(),
            sideMenuWidthState.asStateFlow()
        ) { configuration, width ->
            width ?: configuration.sideMenuWidth
        }.stateIn(viewModelScope, SharingStarted.Lazily, 280F)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val menuItemsPerFolderId: StateFlow<Map<String, List<MenuItem>>> by lazy {
        combine(
            authManager.listenForUser(),
            notesNavigationUseCase.navigationState
        ) { user, notesNavigation ->
            user to notesNavigation
        }.flatMapLatest { (user, notesNavigation) ->
            notesUseCase.listenForMenuItemsPerFolderId(notesNavigation, user.id)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    }

    override val sideMenuItems: StateFlow<List<MenuItemUi>> by lazy {
        combine(
            _expandedFolders,
            menuItemsPerFolderId,
            highlightItem
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
                    filterPredicate = { menuItemUi ->
                        expanded.contains(menuItemUi.documentId)
                    }
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

    override fun init() {
        viewModelScope.launch {
            _workspaceLocalPath.value =
                workspaceConfigRepository.loadWorkspacePath(DISCONNECTED_USER_ID) ?: ""
        }
    }

    override fun expandFolder(id: String) {
        val expanded = _expandedFolders.value
        if (expanded.contains(id)) {
            viewModelScope.launch(Dispatchers.Default) {
                _expandedFolders.value = expanded - id
            }
        } else {
            viewModelScope.launch {
                notesUseCase.listenForMenuItemsByParentId(id, getUserId())
                _expandedFolders.value = expanded + id
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
                uiConfigurationRepo.getUiConfigurationEntity("disconnected_user")
                    ?: UiConfiguration(
                        userId = "disconnected_user",
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
        _showSearch.value = true
    }

    override fun hideSearch() {
        _showSearch.value = false
    }

    override fun changeIcons(menuItemId: String, icon: String, tint: Int, iconChange: IconChange) {
        viewModelScope.launch {
            when (iconChange) {
                IconChange.FOLDER -> notesUseCase.updateFolderById(menuItemId) { folder ->
                    folder.copy(icon = MenuItem.Icon(icon, tint))
                }

                IconChange.DOCUMENT -> notesUseCase.updateDocumentById(menuItemId) { document ->
                    document.copy(icon = MenuItem.Icon(icon, tint))
                }
            }
        }
    }

    override fun changeWorkspaceLocalPath(path: String) {
        viewModelScope.launch(Dispatchers.Default) {
            workspaceConfigRepository.saveWorkspacePath(path, DISCONNECTED_USER_ID)
            _workspaceLocalPath.value =
                workspaceConfigRepository.loadWorkspacePath(DISCONNECTED_USER_ID) ?: ""
        }
    }

    override fun changeOllamaUrl(url: String) {
        viewModelScope.launch(Dispatchers.Default) {
            ollamaRepository.saveOllamaUrl("disconnected_user", url)
        }
    }

    override fun selectOllamaModel(model: String) {
        viewModelScope.launch(Dispatchers.Default) {
            ollamaRepository.saveOllamaSelectedModel("disconnected_user", model)
        }
    }

    override fun retryModels() {
        _retryModels.value = Random.nextInt()
    }

    override fun modelToDownload(model: String, onComplete: () -> Unit) {
        if (model.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val url = ollamaRepository.getConfiguredOllamaUrl()?.trim()

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
                            ollamaRepository.saveOllamaSelectedModel(
                                "disconnected_user",
                                modelsResult.data.models.first().model
                            )
                        }
                    }
            }
        }
    }

    override fun deleteModel(model: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val url = ollamaRepository.getConfiguredOllamaUrl()?.trim()

            if (url != null) {
                ollamaRepository.deleteModel(model, url)

                retryModels()
            }
        }
    }

    private suspend fun getUserId(): String =
        localUserId ?: authManager.getUser().id.also { id ->
            localUserId = id
        }
}
