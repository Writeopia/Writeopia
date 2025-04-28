package io.writeopia.common.utils

sealed class NotesNavigation(val navigationType: NotesNavigationType, val id: String) {
    data object Favorites :
        NotesNavigation(navigationType = NotesNavigationType.FAVORITES, "favorites")

    class Folder(folderId: String) :
        NotesNavigation(navigationType = NotesNavigationType.FOLDER, id = folderId)

    data object Root : NotesNavigation(navigationType = NotesNavigationType.ROOT, "root")

    companion object {
        fun fromType(type: NotesNavigationType, path: String): NotesNavigation =
            when (type) {
                NotesNavigationType.FAVORITES -> Favorites
                NotesNavigationType.ROOT -> Root
                NotesNavigationType.FOLDER -> Folder(path)
            }
    }
}

enum class NotesNavigationType(val type: String) {
    FAVORITES("favorites"),
    ROOT("root"),
    FOLDER("folder");

    companion object {
        fun fromType(type: String) = entries.firstOrNull { it.type == type }
            ?: throw IllegalArgumentException("Could not find type: $type")
    }
}
