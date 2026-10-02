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
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FolderStateController private constructor(
    private val notesUseCase: NotesUseCase,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi,
) : FolderController {
    // The controller is shared by every notes screen, so it can't borrow a ViewModel scope: on
    // mobile each folder screen has its own ViewModel and leaving the folder would cancel the
    // scope, silently dropping every later folder change. The scope lives as long as the
    // instance and is cancelled with it.
    private val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var localUserId: String? = null

    private val _selectedNotes = MutableStateFlow<Set<String>>(setOf())
    override val selectedNotes: StateFlow<Set<String>> = _selectedNotes.asStateFlow()

    // Todo: Change this to a usecase
    private val editingFolderMutable = MutableStateFlow<MenuItemUi.FolderUi?>(null)
    val editingFolderState = editingFolderMutable.asStateFlow()

    // Bumped after a folder is saved locally. The folder lists only change when what's inside a
    // folder changes, so screens showing a folder itself (its name, its icon) listen to this.
    private val _folderChanges = MutableStateFlow(0L)
    val folderChanges: StateFlow<Long> = _folderChanges.asStateFlow()

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
            _folderChanges.update { it + 1 }
            syncFolderToBackend(updatedFolder)
        }
    }

    override fun deleteFolder(id: String, onDeleted: () -> Unit) {
        coroutineScope.launch(Dispatchers.Default) {
            val workspaceId = authRepository.getWorkspace()?.id
                ?: Workspace.disconnectedWorkspace().id

            // Soft delete locally first (optimistic delete - folder disappears from UI)
            notesUseCase.deleteFolderById(id, workspaceId)
            stopEditingFolder()
            withContext(Dispatchers.Main) { onDeleted() }

            // Try to sync folder deletion to backend
            val syncSuccess = syncFolderDeletionToBackend(id)

            // If backend sync succeeded, hard delete locally
            // If sync failed, folder remains soft-deleted and will be retried during EventSync
            if (syncSuccess) {
                notesUseCase.hardDeleteFolderById(id, workspaceId)
            }
        }
    }

    /**
     * Attempts to sync folder deletion to backend.
     * @return true if sync succeeded, false if it failed (offline, error, etc.)
     */
    private suspend fun syncFolderDeletionToBackend(folderId: String): Boolean {
        if (!authRepository.isLoggedIn()) return true // No backend to sync to
        if (authRepository.getUser().tier != Tier.PREMIUM) return true // No sync for non-premium

        val workspace = authRepository.getWorkspace() ?: return true
        // Skip sync for offline/disconnected workspace
        if (workspace.id == Workspace.disconnectedWorkspace().id) return true

        return when (
            documentsApi.deleteFolder(
                folderId = folderId,
                workspaceId = workspace.id
            )
        ) {
            is ResultData.Complete -> true
            is ResultData.Error -> false
            is ResultData.Idle -> true
            is ResultData.Loading -> false
            is ResultData.InProgress -> false
        }
    }

    private suspend fun syncFolderToBackend(folder: Folder) {
        if (!authRepository.isLoggedIn()) return
        if (authRepository.getUser().tier != Tier.PREMIUM) return

        val workspace = authRepository.getWorkspace() ?: return
        if (workspace.id == Workspace.disconnectedWorkspace().id) return
        if (folder.workspaceId != workspace.id) return

        val result = documentsApi.sendFolders(
            folders = listOf(folder),
            workspaceId = workspace.id
        )

        if (result is ResultData.Complete) {
            val syncedFolder = folder.copy(lastSyncedAt = Clock.System.now())
            notesUseCase.updateFolder(syncedFolder)
        }
    }

    override fun syncFolder(folder: Folder) {
        coroutineScope.launch(Dispatchers.Default) {
            syncFolderToBackend(folder)
        }
    }

    override fun stopEditingFolder() {
        editingFolderMutable.value = null
    }

    private val _moveSelectionState = MutableStateFlow(false)
    override val moveSelectionState: StateFlow<Boolean> = _moveSelectionState.asStateFlow()

    override fun showMoveSelection() {
        if (_selectedNotes.value.isNotEmpty()) {
            _moveSelectionState.value = true
        }
    }

    override fun hideMoveSelection() {
        _moveSelectionState.value = false
    }

    override fun moveSelectionTo(parentId: String) {
        val ids = _selectedNotes.value - parentId
        _moveSelectionState.value = false
        if (ids.isEmpty()) return

        coroutineScope.launch(Dispatchers.Default) {
            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
            val moved = notesUseCase.moveItemsById(ids = ids, workspaceId = workspace.id, parentId = parentId)
            _selectedNotes.value = emptySet()
            _folderChanges.update { it + 1 }
            sendMovesToBackend(moved, parentId, workspace)
        }
    }

    /**
     * Tells the backend about the moves right away, so it records the events. When it can't be
     * reached, the moved items stay outdated locally and the next workspace sync sends them.
     */
    private suspend fun sendMovesToBackend(moved: List<MenuItem>, parentId: String, workspace: Workspace) {
        moved.forEach { item ->
            sendMoveToBackend(item.id, isFolder = item is Folder, parentId, workspace)
        }
    }

    private suspend fun sendMoveToBackend(
        id: String,
        isFolder: Boolean,
        parentId: String,
        workspace: Workspace
    ) {
        if (workspace.id == Workspace.disconnectedWorkspace().id) return

        // Best effort: without the backend the item stays outdated locally and the next
        // workspace sync sends it.
        runCatching {
            if (isFolder) {
                documentsApi.moveFolder(id, parentId, workspace.id)
            } else {
                documentsApi.moveDocument(id, parentId, workspace.id)
            }
        }
    }

    override fun moveToFolder(menuItemUi: MenuItemUi, parentId: String) {
        if (menuItemUi.documentId != parentId) {
            coroutineScope.launch(Dispatchers.Default) {
                val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
                if (_selectedNotes.value.isEmpty()) {
                    if (moveItemToFolder(menuItemUi, parentId)) {
                        sendMoveToBackend(
                            menuItemUi.documentId,
                            isFolder = menuItemUi is MenuItemUi.FolderUi,
                            parentId,
                            workspace
                        )
                    }
                } else {
                    val moved = notesUseCase.moveItemsById(
                        ids = selectedNotes.value,
                        parentId,
                        workspace.id
                    )
                    sendMovesToBackend(moved, parentId, workspace)
                }
            }
        }
    }

    /** Moves the item; false when a folder would be moved into itself. */
    private suspend fun moveItemToFolder(menuItemUi: MenuItemUi, parentId: String): Boolean {
        if (menuItemUi is MenuItemUi.FolderUi &&
            menuItemUi.anyNode { node -> node.id == parentId }
        ) {
            return false
        }

        notesUseCase.moveItem(menuItemUi, parentId)
        return true
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
                    _folderChanges.update { it + 1 }
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
        private var instance: FolderStateController? = null

        fun singleton(
            notesUseCase: NotesUseCase,
            authRepository: AuthRepository,
            documentsApi: DocumentsApi
        ) =
            instance ?: FolderStateController(notesUseCase, authRepository, documentsApi)
                .also {
                    instance = it
                }

        fun clearInstance() {
            instance?.clearSessionState()
            instance = null
        }
    }

    private fun clearSessionState() {
        _selectedNotes.value = emptySet()
        editingFolderMutable.value = null
        localUserId = null
        coroutineScope.cancel()
    }
}
