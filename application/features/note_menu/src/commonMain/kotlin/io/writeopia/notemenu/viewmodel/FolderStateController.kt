package io.writeopia.notemenu.viewmodel

import io.writeopia.auth.core.manager.AuthManager
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.common.utils.anyNode
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.sdk.models.document.Folder
import io.writeopia.notemenu.data.usecase.NotesUseCase
import io.writeopia.sdk.models.document.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderStateController(
    private val notesUseCase: NotesUseCase,
    private val authManager: AuthManager,
) : FolderController {
    private lateinit var coroutineScope: CoroutineScope

    private var localUserId: String? = null

    // Todo: Change this to a usecase
    private val _editingFolder = MutableStateFlow<MenuItemUi.FolderUi?>(null)
    val editingFolderState = _editingFolder.asStateFlow()

    fun initCoroutine(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    override fun addFolder() {
        coroutineScope.launch(Dispatchers.Default) {
            notesUseCase.createFolder("Untitled", getUserId())
        }
    }

    override fun editFolder(folder: MenuItemUi.FolderUi) {
        _editingFolder.value = folder
    }

    override fun updateFolder(folderEdit: Folder) {
        coroutineScope.launch(Dispatchers.Default) {
            notesUseCase.updateFolder(folderEdit)
        }
    }

    override fun deleteFolder(id: String) {
        coroutineScope.launch(Dispatchers.Default) {
            notesUseCase.deleteFolderById(id)
            stopEditingFolder()
        }
    }

    override fun stopEditingFolder() {
        _editingFolder.value = null
    }

    override fun moveToFolder(menuItemUi: MenuItemUi, parentId: String) {
        if (menuItemUi.documentId != parentId) {
            coroutineScope.launch(Dispatchers.Default) {
                // Avoid cyclical graphs
                if (menuItemUi is MenuItemUi.FolderUi &&
                    menuItemUi.anyNode { node -> node.id == parentId }
                ) {
                    return@launch
                }

                notesUseCase.moveItem(menuItemUi, parentId)
            }
        }
    }

    override fun changeIcons(
        menuItemId: String,
        icon: String,
        tint: Int,
        iconChange: IconChange
    ) {
        coroutineScope.launch {
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

    private suspend fun getUserId(): String =
        localUserId ?: authManager.getUser().id.also { id ->
            localUserId = id
        }
}
