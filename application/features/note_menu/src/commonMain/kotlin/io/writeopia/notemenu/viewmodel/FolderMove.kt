package io.writeopia.notemenu.viewmodel

/**
 * Items dropped into a folder, kept so the move can be undone.
 *
 * @param itemTitle The title of the item that was dragged.
 * @param folderTitle The title of the folder the items went to, null for the home folder.
 * @param items Every moved item, with the folder it was in before.
 */
data class FolderMove(
    val itemTitle: String,
    val folderTitle: String?,
    val items: List<MovedItem>,
) {
    data class MovedItem(val id: String, val isFolder: Boolean, val previousParentId: String)
}
