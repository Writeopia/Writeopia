package io.writeopia.features.search.repository

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.features.search.api.SearchApi
import io.writeopia.models.interfaces.search.FolderSearch
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.search.DocumentSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class SearchRepository(
    private val folderSearch: FolderSearch,
    private val documentSearch: DocumentSearch,
    private val searchApi: SearchApi,
    private val authRepository: AuthRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchNotesAndFoldersLocally(query: String): Flow<List<SearchItem>> {
        val workspaceFlow = authRepository.listenForWorkspace()

        if (query.isEmpty()) return workspaceFlow.flatMapLatest { workspace ->
            flow { emit(getNotesAndFolders(workspace.id)) }
        }

        val foldersFlow: Flow<List<Folder>> = workspaceFlow.flatMapLatest { workspace ->
            flow { emit(folderSearch.search(query, workspace.id)) }
        }

        val documentsFlow: Flow<List<Document>> =
            authRepository.listenForWorkspace().flatMapLatest { workspace ->
                flow {
                    emit(
                        documentSearch.search(
                            query,
                            workspace.id,
                        )
                    )
                }
            }

        return combine(foldersFlow, documentsFlow) { folders, documents ->
            (folders + documents).toSearchItems()
        }
    }

    fun searchNotesAndFoldersRemotely(query: String): Flow<List<SearchItem>> {
        return flow {
//            println("triggering documentsApiFlow")
//            emit(emptyList())
//            println("calling api")
            emit(searchApi.searchApi(query).toSearchItems())
        }
    }

    private suspend fun getNotesAndFolders(workspaceId: String): List<SearchItem> {
        val folders = folderSearch.getLastUpdated()
        val documents = documentSearch.getLastUpdatedAt(workspaceId)

        return (folders + documents).toSearchItems()
    }
}

private fun List<MenuItem>.toSearchItems(): List<SearchItem> =
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
