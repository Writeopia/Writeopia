package io.writeopia.commonui.folders

import io.writeopia.sdk.models.document.Folder

/** A folder found by the search of the move picker, with where it is: "Home / Work / 2026". */
data class FolderSearchResult(val folder: Folder, val path: String)

/**
 * The folders the selected items can be moved into, to browse level by level or to search.
 * The [excludedIds] (the selected folders) are left out, and so is everything inside them, as a
 * folder can't go inside itself.
 */
class MoveFolderTree(
    folders: List<Folder>,
    excludedIds: Set<String>,
    private val rootTitle: String,
) {
    private val childrenByParent: Map<String, List<Folder>> = folders
        .filter { folder -> !folder.deleted && !excludedIds.contains(folder.id) }
        .groupBy { folder -> folder.parentId }
        .mapValues { (_, children) -> children.sortedBy { folder -> folder.title.lowercase() } }

    // Only what hangs from the root: the inside of an excluded folder is never reached.
    private val reachable: List<FolderSearchResult> = buildList {
        fun visit(parentId: String, path: String) {
            childrenOf(parentId).forEach { folder ->
                add(FolderSearchResult(folder, path))
                visit(folder.id, "$path / ${folder.title}")
            }
        }

        visit(Folder.ROOT_PATH, rootTitle)
    }

    fun childrenOf(parentId: String): List<Folder> = childrenByParent[parentId].orEmpty()

    /** The folders whose title contains [query], ignoring case. Every folder when it's blank. */
    fun search(query: String): List<FolderSearchResult> {
        val term = query.trim()

        return if (term.isEmpty()) {
            reachable
        } else {
            reachable.filter { result -> result.folder.title.contains(term, ignoreCase = true) }
        }
    }
}
