package io.writeopia.global.shell.viewmodel

import io.writeopia.auth.core.manager.AuthManager
import io.writeopia.note_menu.data.model.Folder
import io.writeopia.note_menu.data.model.NotesNavigation
import io.writeopia.note_menu.data.usecase.NoteNavigationUseCase
import io.writeopia.note_menu.data.usecase.NotesUseCase
import io.writeopia.note_menu.extensions.toUiCard
import io.writeopia.note_menu.ui.dto.MenuItemUi
import io.writeopia.repository.UiConfigurationRepository
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.utils_module.KmpViewModel
import io.writeopia.utils_module.collections.toNodeTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SideMenuKmpViewModel(
    private val notesUseCase: NotesUseCase,
    private val uiConfigurationRepo: UiConfigurationRepository,
    private val authManager: AuthManager,
    private val notesNavigationUseCase: NoteNavigationUseCase
) : SideMenuViewModel, KmpViewModel() {

    private var localUserId: String? = null
    private val _editingFolder = MutableStateFlow<MenuItemUi.FolderUi?>(null)

    private val _expandedFolders = MutableStateFlow(setOf<String>())
    override val expandedFolders: StateFlow<Set<String>> = _expandedFolders.asStateFlow()

    private val _showSettingsState = MutableStateFlow(false)
    override val showSettingsState: StateFlow<Boolean> = _showSettingsState.asStateFlow()

    override val highlightItem: StateFlow<String?> by lazy {
        notesNavigationUseCase.navigationState
            .map { navigation -> navigation.id }
            .stateIn(coroutineScope, SharingStarted.Lazily, NotesNavigation.Root.id)
    }

    override val showSideMenu: StateFlow<Boolean> by lazy {
        uiConfigurationRepo.listenForUiConfiguration(::getUserId, coroutineScope)
            .map { configuration ->
                configuration?.showSideMenu ?: true
            }.stateIn(coroutineScope, SharingStarted.Lazily, false)
    }

    override fun addFolder() {
        coroutineScope.launch(Dispatchers.Default) {
            notesUseCase.createFolder("Untitled", getUserId())
        }
    }

    override fun editFolder(folder: MenuItemUi.FolderUi) {
        _editingFolder.value = folder
    }

    override val menuItemsPerFolderId: StateFlow<Map<String, List<MenuItem>>> by lazy {
        notesNavigationUseCase.navigationState.flatMapLatest { notesNavigation ->
            when (notesNavigation) {
                NotesNavigation.Favorites -> notesUseCase.listenForMenuItemsByParentId(
                    Folder.ROOT_PATH,
                    ::getUserId,
                    coroutineScope
                )

                is NotesNavigation.Folder -> notesUseCase.listenForMenuItemsByParentId(
                    notesNavigation.id,
                    ::getUserId,
                    coroutineScope
                )

                NotesNavigation.Root -> notesUseCase.listenForMenuItemsByParentId(
                    Folder.ROOT_PATH,
                    ::getUserId,
                    coroutineScope
                )
            }
        }.stateIn(coroutineScope, SharingStarted.Lazily, emptyMap())
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
                .toList() as List<MenuItemUi>

            itemsList.toMutableList().apply {
                removeAt(0)
            }
        }.stateIn(coroutineScope, SharingStarted.Lazily, emptyList())
    }

    override fun showSettings() {
        _showSettingsState.value = true
    }

    override fun hideSettings() {
        _showSettingsState.value = false
    }

    override fun moveToFolder(menuItemUi: MenuItemUi, parentId: String) {
        if (menuItemUi.documentId != parentId) {
            coroutineScope.launch(Dispatchers.Default) {
                notesUseCase.moveItem(menuItemUi, parentId)
            }
        }
    }

    override fun expandFolder(id: String) {
        val expanded = _expandedFolders.value
        if (expanded.contains(id)) {
            coroutineScope.launch(Dispatchers.Default) {
                _expandedFolders.value = expanded - id
            }
        } else {
            notesUseCase.listenForMenuItemsByParentId(id, ::getUserId, coroutineScope)
            _expandedFolders.value = expanded + id
        }
    }

    private suspend fun getUserId(): String =
        localUserId ?: authManager.getUser().id.also { id ->
            localUserId = id
        }
}
