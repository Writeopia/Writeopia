package io.writeopia.claudecode.mcp

/** A folder as Claude Code sees it. */
data class LiveFolder(val id: String, val name: String, val parentId: String)

/**
 * The answer of list_folders: the folders as an indented tree, siblings by name, each with its
 * folder_id, and which one the app has open. Mirrored word for word by `FolderTree.swift`.
 */
object FolderTree {
    const val ROOT = "root"

    fun describe(folders: List<LiveFolder>, currentFolderId: String): String {
        val byId = folders.associateBy { it.id }
        val current = byId[currentFolderId]
        val open = if (current == null) {
            "The app has the top level open: new documents and folders go there unless you pass a folder_id."
        } else {
            "The app has \"${current.name}\" (folder_id: ${current.id}) open: new documents and folders go " +
                "there unless you pass a folder_id."
        }

        if (folders.isEmpty()) {
            return "Writeopia has no folders yet: everything is in the top level. Make one with create_folder."
        }

        // A folder whose parent isn't listed sits at the top level, so none goes missing.
        val children = folders.groupBy { folder -> if (folder.parentId in byId) folder.parentId else ROOT }
        val lines = mutableListOf<String>()

        fun walk(parentId: String, depth: Int, seen: Set<String>) {
            children[parentId].orEmpty()
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .filter { it.id !in seen }
                .forEach { folder ->
                    lines.add("${"  ".repeat(depth)}- ${folder.name} (folder_id: ${folder.id})")
                    walk(folder.id, depth + 1, seen + folder.id)
                }
        }
        walk(ROOT, 0, emptySet())

        return "Folders in Writeopia (${folders.size}):\n" + lines.joinToString("\n") + "\n\n" + open
    }
}
