package io.writeopia.features.search.repository

import io.writeopia.features.search.api.SearchApi
import io.writeopia.sdk.models.document.Folder
import io.writeopia.models.search.FolderSearch
import io.writeopia.sdk.models.document.Document
import io.writeopia.models.interfaces.search.FolderSearch
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.search.DocumentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class SearchRepository(
    private val folderSearch: FolderSearch,
    private val documentSearch: DocumentSearch,
    private val searchApi: SearchApi,
) {
    fun searchNotesAndFolders(query: String): Flow<List<SearchItem>> {
        if (query.isEmpty()) return flow { emit(getNotesAndFolders()) }

        val foldersFlow: Flow<List<Folder>> = flow {
            emit(folderSearch.search(query))
        }
        val documentsFlow: Flow<List<Document>> = flow {
            emit(documentSearch.search(query))
        }
        val documentsApiFlow: Flow<List<Document>> = flow {
            emit(emptyList())
            emit(searchApi.searchApi(query))
        }

        return combine(
            foldersFlow,
            documentsFlow,
            documentsApiFlow
        ) { folders, documents, documentsApi ->
            folders + documents + documentsApi
        }.map { menuItems ->
            menuItems.toSearchItems()
        }
    }

    private suspend fun getNotesAndFolders(): List<SearchItem> {
        val folders = folderSearch.getLastUpdated()
        val documents = documentSearch.getLastUpdatedAt()

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
