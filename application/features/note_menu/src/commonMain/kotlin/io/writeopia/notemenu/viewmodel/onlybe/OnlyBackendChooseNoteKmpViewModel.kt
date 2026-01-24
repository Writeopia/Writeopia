package io.writeopia.notemenu.viewmodel.onlybe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.NotesNavigationType
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.commonui.extensions.toUiCard
import io.writeopia.core.configuration.models.NotesArrangement
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.notemenu.ui.dto.NotesUi
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel
import io.writeopia.notemenu.viewmodel.ConfigState
import io.writeopia.notemenu.viewmodel.FolderController
import io.writeopia.notemenu.viewmodel.SyncState
import io.writeopia.notemenu.viewmodel.UserState
import io.writeopia.onboarding.OnboardingState
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.utils.map
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.preview.PreviewParser
import io.writeopia.sdk.serialization.extensions.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

/**
 * ViewModel that only interacts with the backend, without any local database operations.
 * All data is fetched from and sent to the backend API.
 */
internal class OnlyBackendChooseNoteKmpViewModel(
    private val documentsApi: DocumentsApi,
    private val authRepository: AuthRepository,
    private val notesNavigation: NotesNavigation = NotesNavigation.Root,
    private val previewParser: PreviewParser = PreviewParser(),
) : ChooseNoteViewModel, ViewModel(), FolderController {

    private val _menuItemsPerFolderId = MutableStateFlow<Map<String, List<MenuItem>>>(emptyMap())
    override val menuItemsPerFolderId: StateFlow<Map<String, List<MenuItem>>> =
        _menuItemsPerFolderId.asStateFlow()

    private val _menuItemsState = MutableStateFlow<ResultData<List<MenuItem>>>(ResultData.Loading())
    override val menuItemsState: StateFlow<ResultData<List<MenuItem>>> =
        _menuItemsState.asStateFlow()

    private val _selectedNotes = MutableStateFlow<Set<String>>(emptySet())
    override val selectedNotes: StateFlow<Set<String>> = _selectedNotes.asStateFlow()

    override val hasSelectedNotes: StateFlow<Boolean> by lazy {
        _selectedNotes.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())
            .let { flow ->
                MutableStateFlow(false).also { result ->
                    viewModelScope.launch {
                        flow.collect { selected ->
                            result.value = selected.isNotEmpty()
                        }
                    }
                }
            }
    }

    private val _userName = MutableStateFlow<UserState<String>>(UserState.Idle())
    override val userName: StateFlow<UserState<String>> = _userName.asStateFlow()

    private val _notesArrangement = MutableStateFlow(NotesArrangement.GRID)
    override val notesArrangement: StateFlow<NotesArrangement> = _notesArrangement.asStateFlow()

    private val _orderByState = MutableStateFlow(OrderBy.UPDATE)
    override val orderByState: StateFlow<OrderBy> = _orderByState.asStateFlow()

    private val _editState = MutableStateFlow(false)
    override val editState: StateFlow<Boolean> = _editState.asStateFlow()

    private val _showSortMenuState = MutableStateFlow(false)
    override val showSortMenuState: StateFlow<Boolean> = _showSortMenuState.asStateFlow()

    private val _showLocalSyncConfigState = MutableStateFlow<ConfigState>(ConfigState.Idle)
    override val showLocalSyncConfigState: StateFlow<ConfigState> =
        _showLocalSyncConfigState.asStateFlow()

    private val _syncInProgress = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncInProgress: StateFlow<SyncState> = _syncInProgress.asStateFlow()

    private val _titlesToDelete = MutableStateFlow<List<String>>(emptyList())
    override val titlesToDelete: StateFlow<List<String>> = _titlesToDelete.asStateFlow()

    private val _showOnboardingState = MutableStateFlow(OnboardingState.COMPLETE)
    override val showOnboardingState: StateFlow<OnboardingState> =
        _showOnboardingState.asStateFlow()

    private val _showAddMenuState = MutableStateFlow(false)
    override val showAddMenuState: StateFlow<Boolean> = _showAddMenuState.asStateFlow()

    private val _editingFolder = MutableStateFlow<MenuItemUi.FolderUi?>(null)
    override val editFolderState: StateFlow<Folder?> by lazy {
        combine(
            _editingFolder,
            _menuItemsPerFolderId,
        ) { selectedFolder, menuItems ->
            if (selectedFolder != null) {
                menuItems[selectedFolder.parentId]
                    ?.find { menuItem -> menuItem.id == selectedFolder.id } as? Folder
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    override val documentsState: StateFlow<ResultData<NotesUi>> by lazy {
        combine(
            _selectedNotes,
            _menuItemsState,
            _notesArrangement
        ) { selectedNoteIds, resultData, arrangement ->
            val previewLimit = 4

            resultData.map { documentList ->
                NotesUi(
                    documentUiList = documentList.map { menuItem ->
                        menuItem.toUiCard(
                            previewParser = previewParser,
                            selected = selectedNoteIds.contains(menuItem.id),
                            limit = previewLimit,
                            expanded = false,
                        )
                    },
                    notesArrangement = arrangement
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Idle())
    }

    private val askToDelete = MutableStateFlow(false)

    init {
        // Set up titlesToDelete based on askToDelete and selection
        viewModelScope.launch {
            combine(
                askToDelete,
                _selectedNotes,
                _menuItemsState
            ) { shouldAsk, selectedIds, itemsState ->
                if (shouldAsk && itemsState is ResultData.Complete) {
                    itemsState.data
                        .filter { item -> selectedIds.contains(item.id) }
                        .map { item -> item.title }
                } else {
                    emptyList()
                }
            }.collect { titles ->
                _titlesToDelete.value = titles
            }
        }

        // Initial load
        loadFolderContents()
    }

    private fun loadFolderContents() {
        viewModelScope.launch(Dispatchers.Default) {
            _menuItemsState.value = ResultData.Loading()

            val token = authRepository.getAuthToken()
            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()

            if (token == null) {
                _menuItemsState.value = ResultData.Error()
                return@launch
            }

            val folderId = when (notesNavigation) {
                is NotesNavigation.Folder -> notesNavigation.id
                NotesNavigation.Root, NotesNavigation.Favorites -> Folder.ROOT_PATH
            }

            val result = documentsApi.getFolderContents(folderId, workspace.id, token)

            if (result is ResultData.Complete) {
                val contents = result.data
                val folders: List<MenuItem> = contents.folders.map { it.toModel() }
                val documents: List<MenuItem> = contents.documents.map { it.toModel() }
                val allItems = folders + documents

                // Update the menu items map
                val newMap = _menuItemsPerFolderId.value.toMutableMap()
                newMap[folderId] = allItems
                _menuItemsPerFolderId.value = newMap

                // Filter for favorites if needed
                val pageItems = when (notesNavigation) {
                    NotesNavigation.Favorites -> allItems.filter { it.favorite }
                    else -> allItems
                }

                _menuItemsState.value = ResultData.Complete(pageItems)
            } else {
                _menuItemsState.value = ResultData.Error()
            }
        }
    }

    override suspend fun requestUser() {
        try {
            if (authRepository.isLoggedIn()) {
                val user = authRepository.getUser()
                _userName.value = UserState.ConnectedUser(user.name)
            } else {
                _userName.value = UserState.DisconnectedUser(WriteopiaUser.disconnectedUser().name)
            }
        } catch (error: Exception) {
            _userName.value = UserState.Idle()
        }
    }

    override fun handleMenuItemTap(id: String): Boolean {
        return if (_selectedNotes.value.isNotEmpty()) {
            toggleSelection(id)
            true
        } else {
            false
        }
    }

    override fun showEditMenu() {
        _editState.value = true
    }

    override fun cancelEditMenu() {
        _editState.value = false
    }

    override fun showSortMenu() {
        _showSortMenuState.value = true
    }

    override fun cancelSortMenu() {
        _showSortMenuState.value = false
    }

    override fun showAddMenu() {
        _showAddMenuState.value = true
    }

    override fun hideAddMenu() {
        _showAddMenuState.value = false
    }

    override fun listArrangementSelected() {
        _notesArrangement.value = NotesArrangement.LIST
    }

    override fun gridArrangementSelected() {
        _notesArrangement.value = NotesArrangement.GRID
    }

    override fun staggeredGridArrangementSelected() {
        _notesArrangement.value = NotesArrangement.STAGGERED_GRID
    }

    @OptIn(ExperimentalTime::class)
    override fun sortingSelected(orderBy: OrderBy) {
        _orderByState.value = orderBy
        // Re-sort the current items
        val currentState = _menuItemsState.value
        if (currentState is ResultData.Complete) {
            val sorted = when (orderBy) {
                OrderBy.CREATE -> currentState.data.sortedByDescending { it.createdAt }
                OrderBy.UPDATE -> currentState.data.sortedByDescending { it.lastUpdatedAt }
                OrderBy.NAME -> currentState.data.sortedBy { it.title.lowercase() }
            }
            _menuItemsState.value = ResultData.Complete(sorted)
        }
    }

    override fun copySelectedNotes() {
        // Not supported in backend-only mode
    }

    override fun deleteSelectedNotes() {
        viewModelScope.launch(Dispatchers.Default) {
            val token = authRepository.getAuthToken() ?: return@launch
            val workspace = authRepository.getWorkspace() ?: return@launch
            val selectedIds = _selectedNotes.value

            // Separate folders and documents based on current menu items
            val currentItems = (_menuItemsState.value as? ResultData.Complete)?.data ?: emptyList()
            val folderIds = mutableListOf<String>()
            val documentIds = mutableListOf<String>()

            selectedIds.forEach { id ->
                val item = currentItems.find { it.id == id }
                when (item) {
                    is Folder -> folderIds.add(id)
                    else -> documentIds.add(id)
                }
            }

            // Delete folders via API (recursive deletion on backend)
            folderIds.forEach { folderId ->
                documentsApi.deleteFolder(folderId, workspace.id, token)
            }

            // Delete documents via API
            if (documentIds.isNotEmpty()) {
                documentsApi.deleteDocuments(documentIds, workspace.id, token)
            }

            clearSelection()
            askToDelete.value = false

            // Refresh the content
            loadFolderContents()
        }
    }

    override fun favoriteSelectedNotes() {
        // Not supported in backend-only mode without additional API endpoints
    }

    override fun summarizeDocuments() {
        // Not supported in backend-only mode
    }

    override fun requestPermissionToDeleteSelection() {
        askToDelete.value = true
    }

    override fun cancelDeletion() {
        askToDelete.value = false
    }

    override fun syncFolderWithCloud() {
        // Already using backend, just refresh
        loadFolderContents()
    }

    override fun newFolder() {
        viewModelScope.launch(Dispatchers.Default) {
            val token = authRepository.getAuthToken() ?: return@launch
            val workspace = authRepository.getWorkspace() ?: return@launch

            val parentId = when (notesNavigation.navigationType) {
                NotesNavigationType.FOLDER -> notesNavigation.id
                else -> Folder.ROOT_PATH
            }

            val result = documentsApi.createFolder(
                parentFolderId = parentId,
                title = "Untitled",
                workspaceId = workspace.id,
                token = token
            )

            if (result is ResultData.Complete) {
                // Fetch the contents of the newly created folder
                documentsApi.getFolderContents(result.data.id, workspace.id, token)
                // Refresh the parent folder contents
                loadFolderContents()
            }
        }
    }

    // FolderController implementation
    override fun addFolder(parentId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val token = authRepository.getAuthToken() ?: return@launch
            val workspace = authRepository.getWorkspace() ?: return@launch

            val result = documentsApi.createFolder(
                parentFolderId = parentId,
                title = "Untitled",
                workspaceId = workspace.id,
                token = token
            )

            if (result is ResultData.Complete) {
                documentsApi.getFolderContents(result.data.id, workspace.id, token)
                loadFolderContents()
            }
        }
    }

    override fun editFolder(folder: MenuItemUi.FolderUi) {
        _editingFolder.value = folder
    }

    override fun updateFolder(folderEdit: Folder) {
        // Would need an update folder API endpoint
    }

    override fun deleteFolder(id: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val token = authRepository.getAuthToken() ?: return@launch
            val workspace = authRepository.getWorkspace() ?: return@launch

            documentsApi.deleteFolder(id, workspace.id, token)
            stopEditingFolder()
            loadFolderContents()
        }
    }

    override fun stopEditingFolder() {
        _editingFolder.value = null
    }

    override fun moveToFolder(menuItemUi: MenuItemUi, parentId: String) {
        // Would need a move API endpoint
    }

    override fun changeIcons(menuItemId: String, icon: String, tint: Int, iconChange: IconChange) {
        // Would need an update icon API endpoint
    }

    override fun toggleSelection(id: String) {
        if (_selectedNotes.value.contains(id)) {
            _selectedNotes.value -= id
        } else {
            _selectedNotes.value += id
        }
    }

    override fun onDocumentSelected(id: String, selected: Boolean) {
        if (selected) {
            _selectedNotes.value += id
        } else {
            _selectedNotes.value -= id
        }
    }

    override fun clearSelection() {
        _selectedNotes.value = emptySet()
    }

    // Local sync operations - not supported in backend-only mode
    override fun configureDirectory() {
        // Not supported
    }

    override fun directoryFilesAsMarkdown(path: String) {
        // Not supported
    }

    override fun directoryFilesAsTxt(path: String) {
        // Not supported
    }

    override fun loadFiles(filePaths: List<ExternalFile>) {
        // Not supported
    }

    override fun onSyncLocallySelected() {
        // Not supported
    }

    override fun onWriteLocallySelected() {
        // Not supported
    }

    override fun hideConfigSyncMenu() {
        _showLocalSyncConfigState.value = ConfigState.Idle
    }

    override fun pathSelected(path: String) {
        // Not supported
    }

    override fun confirmWorkplacePath() {
        // Not supported
    }

    override fun requestInitFlow(flow: () -> Unit) {
        flow()
    }

    override fun hideOnboarding() {
        _showOnboardingState.value = OnboardingState.HIDDEN
    }

    override fun closeOnboardingPermanently() {
        _showOnboardingState.value = OnboardingState.COMPLETE
    }

    override fun completeOnboarding() {
        _showOnboardingState.value = OnboardingState.COMPLETE
    }
}
