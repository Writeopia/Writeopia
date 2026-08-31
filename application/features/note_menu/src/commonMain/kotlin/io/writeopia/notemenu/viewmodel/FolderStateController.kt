@file:OptIn(ExperimentalTime::class)

package io.writeopia.notemenu.viewmodel

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.models.user.Tier
import io.writeopia.common.utils.anyNode
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.sdk.models.document.Folder
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FolderStateController private constructor(
    private val notesUseCase: NotesUseCase,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi,
) : FolderController {
    private lateinit var coroutineScope: CoroutineScope

    private var localUserId: String? = null

    private val _selectedNotes = MutableStateFlow<Set<String>>(setOf())
    override val selectedNotes: StateFlow<Set<String>> = _selectedNotes.asStateFlow()

    // Todo: Change this to a usecase
    private val editingFolderMutable = MutableStateFlow<MenuItemUi.FolderUi?>(null)
    val editingFolderState = editingFolderMutable.asStateFlow()

    fun initCoroutine(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    override fun addFolder(parentId: String) {
        coroutineScope.launch(Dispatchers.Default) {
            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
            val folder = notesUseCase.createFolder("Untitled", workspace.id, parentId)
            syncFolderToBackend(folder)
        }
    }

    override fun editFolder(folder: MenuItemUi.FolderUi) {
        editingFolderMutable.value = folder
    }

    override fun updateFolder(folderEdit: Folder) {
        coroutineScope.launch(Dispatchers.Default) {
            val updatedFolder = folderEdit.copy(lastUpdatedAt = Clock.System.now())
            notesUseCase.updateFolder(updatedFolder)
            syncFolderToBackend(updatedFolder)
        }
    }

    override fun deleteFolder(id: String) {
        coroutineScope.launch(Dispatchers.Default) {
            notesUseCase.deleteFolderById(id)
            stopEditingFolder()

            // Sync folder deletion to backend
            syncFolderDeletionToBackend(id)
        }
    }

    private suspend fun syncFolderDeletionToBackend(folderId: String) {
        if (!authRepository.isLoggedIn()) return
        if (authRepository.getUser().tier != Tier.PREMIUM) return

        val workspace = authRepository.getWorkspace() ?: return

        documentsApi.deleteFolder(
            folderId = folderId,
            workspaceId = workspace.id
        )
    }

    private suspend fun syncFolderToBackend(folder: Folder) {
        if (!authRepository.isLoggedIn()) return
        if (authRepository.getUser().tier != Tier.PREMIUM) return

        val workspace = authRepository.getWorkspace() ?: return

        documentsApi.sendFolders(
            folders = listOf(folder),
            workspaceId = workspace.id
        )
    }

    override fun stopEditingFolder() {
        editingFolderMutable.value = null
    }

    override fun moveToFolder(menuItemUi: MenuItemUi, parentId: String) {
        if (menuItemUi.documentId != parentId) {
            coroutineScope.launch(Dispatchers.Default) {
                if (_selectedNotes.value.isEmpty()) {
                    moveItemToFolder(menuItemUi, parentId)
                } else {
                    val workspace =
                        authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()

                    notesUseCase.moveItemsById(
                        ids = selectedNotes.value,
                        parentId,
                        workspace.id
                    )
                }
            }
        }
    }

    private suspend fun moveItemToFolder(menuItemUi: MenuItemUi, parentId: String) {
        if (menuItemUi is MenuItemUi.FolderUi &&
            menuItemUi.anyNode { node -> node.id == parentId }
        ) {
            return
        }

        notesUseCase.moveItem(menuItemUi, parentId)
    }

    override fun changeIcons(
        menuItemId: String,
        icon: String,
        tint: Int,
        iconChange: IconChange
    ) {
        coroutineScope.launch {
            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()

            when (iconChange) {
                IconChange.FOLDER -> {
                    val updatedFolder = notesUseCase.updateFolderById(menuItemId) { folder ->
                        folder.copy(
                            icon = MenuItem.Icon(icon, tint),
                            lastUpdatedAt = Clock.System.now()
                        )
                    }
                    updatedFolder?.let { syncFolderToBackend(it) }
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

    companion object {
        var instance: FolderStateController? = null

        fun singleton(
            notesUseCase: NotesUseCase,
            authRepository: AuthRepository,
            documentsApi: DocumentsApi
        ) =
            instance ?: FolderStateController(notesUseCase, authRepository, documentsApi)
                .also {
                    instance = it
                }
    }
}
