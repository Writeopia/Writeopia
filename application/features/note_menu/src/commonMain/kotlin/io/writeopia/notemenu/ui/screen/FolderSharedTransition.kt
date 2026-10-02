package io.writeopia.notemenu.ui.screen

/**
 * Shared element keys used to make a folder card explode into the folder screen: the card
 * grows into the whole screen while its title flies into the toolbar.
 */
internal object FolderSharedKeys {
    fun card(folderId: String) = "folderTransition$folderId"

    fun title(folderId: String) = "folderTitle$folderId"
}
