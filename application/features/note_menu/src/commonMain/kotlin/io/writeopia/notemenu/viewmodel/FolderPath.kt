package io.writeopia.notemenu.viewmodel

import io.writeopia.common.utils.collections.traverse
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem

/**
 * The folders from the root down to [folderId] (included), following the parent of each folder
 * in this map of items by folder. Empty when the folder isn't among them.
 */
fun Map<String, List<MenuItem>>.pathTo(folderId: String): List<Folder> =
    values.flatten()
        .traverse(
            initialId = folderId,
            targetId = Folder.ROOT_PATH,
            filterPredicate = { item -> item is Folder },
            mapFunc = { item -> item as Folder }
        )

/**
 * The folders from the root down to [folder] (included), looking each parent up with
 * [folderById]. For screens that only hold the contents of the folder shown, not the whole tree.
 * A broken parent chain ends the path where it stops resolving.
 */
suspend fun pathTo(folder: Folder, folderById: suspend (String) -> Folder?): List<Folder> {
    val path = ArrayDeque<Folder>()
    val visited = mutableSetOf<String>()
    var current: Folder? = folder

    // The visited set guards against a cycle in corrupted parent links.
    while (current != null && visited.add(current.id)) {
        path.addFirst(current)
        current = current.parentId
            .takeIf { parentId -> parentId != Folder.ROOT_PATH }
            ?.let { parentId -> folderById(parentId) }
    }

    return path
}
