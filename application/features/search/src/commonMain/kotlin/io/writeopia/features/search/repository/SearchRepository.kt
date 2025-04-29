package io.writeopia.features.search.repository

import io.writeopia.sdk.models.document.Folder
import io.writeopia.models.interfaces.search.FolderSearch
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.search.DocumentSearch

class SearchRepository(
    private val folderSearch: FolderSearch,
    private val documentSearch: DocumentSearch
) {
    suspend fun getNotesAndFolders(): List<SearchItem> {
        val folders = folderSearch.getLastUpdated()
        val documents = documentSearch.getLastUpdatedAt()

        return (folders + documents).toSearchItems()
    }

    suspend fun searchNotesAndFolders(query: String): List<SearchItem> {
        val folders = folderSearch.search(query)
        val documents = documentSearch.search(query)

        return (folders + documents).toSearchItems()
    }
}

fun List<MenuItem>.toSearchItems(): List<SearchItem> =
    this.map { menuItem ->
        when (menuItem) {
            is Folder -> SearchItem.FolderInfo(menuItem.id, menuItem.title)

            else -> SearchItem.DocumentInfo(menuItem.id, menuItem.title)
        }
    }.take(12)

sealed interface SearchItem {
    data class FolderInfo(val id: String, val label: String) : SearchItem

    data class DocumentInfo(val id: String, val label: String) : SearchItem
}
