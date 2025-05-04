package io.writeopia.notemenu.viewmodel

import io.writeopia.common.utils.icons.IconChange
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.flow.StateFlow

interface FolderController {
    val selectedNotes: StateFlow<Set<String>>

    fun addFolder()

    fun editFolder(folder: MenuItemUi.FolderUi)

    fun updateFolder(folderEdit: Folder)

    fun deleteFolder(id: String)

    fun stopEditingFolder()

    fun moveToFolder(menuItemUi: MenuItemUi, parentId: String)

    fun changeIcons(menuItemId: String, icon: String, tint: Int, iconChange: IconChange)

    fun toggleSelection(id: String)

    fun onDocumentSelected(id: String, selected: Boolean)

    fun clearSelection()
}
