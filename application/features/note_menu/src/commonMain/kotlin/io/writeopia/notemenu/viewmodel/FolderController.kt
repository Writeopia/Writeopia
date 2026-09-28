package io.writeopia.notemenu.viewmodel

import io.writeopia.common.utils.icons.IconChange
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.flow.StateFlow

interface FolderController {
    val selectedNotes: StateFlow<Set<String>>

    fun addFolder(parentId: String = "root")

    fun editFolder(folder: MenuItemUi.FolderUi)

    fun updateFolder(folderEdit: Folder)

    /**
     * Deletes the folder. [onDeleted] is called on the main thread once it's deleted locally.
     */
    fun deleteFolder(id: String, onDeleted: () -> Unit = {})

    fun stopEditingFolder()

    fun moveToFolder(menuItemUi: MenuItemUi, parentId: String)

    fun changeIcons(menuItemId: String, icon: String, tint: Int, iconChange: IconChange)

    fun syncFolder(folder: Folder)

    fun toggleSelection(id: String)

    fun onDocumentSelected(id: String, selected: Boolean)

    fun clearSelection()
}
