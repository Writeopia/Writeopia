package io.writeopia.notemenu.viewmodel

import io.writeopia.sdk.models.document.Folder

/**
 * A folder that another folder can be moved into. [title] is null for the root of the workspace.
 * [depth] is how deep it is in the tree, to indent it. [isCurrentParent] marks where the folder
 * already is, so it's shown but can't be picked.
 */
data class FolderDestination(
    val id: String,
    val title: String?,
    val depth: Int,
    val isCurrentParent: Boolean = false,
)

/**
 * The tree of [folders] (root first, then depth first, sorted by title) with the places
 * [movingFolder] can go: not itself and not anything inside it. The folder it is already in is
 * flagged with [FolderDestination.isCurrentParent].
 */
fun moveDestinations(movingFolder: Folder, folders: List<Folder>): List<FolderDestination> {
    val childrenByParent = folders
        .filter { folder -> !folder.deleted && folder.id != movingFolder.id }
        .groupBy { folder -> folder.parentId }

    fun children(parentId: String, depth: Int): List<FolderDestination> =
        childrenByParent[parentId]
            .orEmpty()
            .sortedBy { folder -> folder.title.lowercase() }
            .flatMap { folder ->
                listOf(FolderDestination(folder.id, folder.title, depth)) +
                    children(folder.id, depth + 1)
            }

    // The moving folder is left out of the tree, so what is inside it is never reached.
    return (listOf(FolderDestination(Folder.ROOT_PATH, null, 0)) + children(Folder.ROOT_PATH, 1))
        .map { destination ->
            destination.copy(isCurrentParent = destination.id == movingFolder.parentId)
        }
}
