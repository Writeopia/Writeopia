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

    /** Moves a dragged item (or the selection, when there is one) into [parentId]. */
    fun moveToFolder(menuItemUi: MenuItemUi, parentId: String)

    /** The last drag and drop into a folder, while it can still be undone. */
    val lastMoveState: StateFlow<FolderMove?>

    /** Puts the items of [move] back in the folders they were in. */
    fun undoMove(move: FolderMove)

    /** Forgets [move] once it can no longer be undone. */
    fun dismissMove(move: FolderMove)

    /** Whether the picker of the folder to move the selected items to is shown. */
    val moveSelectionState: StateFlow<Boolean>

    fun showMoveSelection()

    fun hideMoveSelection()

    /**
     * Moves every selected folder and document into [parentId]. Their `lastUpdatedAt` is
     * bumped, so the next sync sends the move to the backend, which records the event.
     */
    fun moveSelectionTo(parentId: String)

    fun changeIcons(menuItemId: String, icon: String, tint: Int, iconChange: IconChange)

    fun syncFolder(folder: Folder)

    fun toggleSelection(id: String)

    fun onDocumentSelected(id: String, selected: Boolean)

    fun clearSelection()
}
