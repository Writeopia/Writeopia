package io.writeopia.features.search.repository

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.features.search.api.SearchApi
import io.writeopia.models.interfaces.search.FolderSearch
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
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
    private val presentationSearch: PresentationSearch = PresentationSearch { _, _ -> emptyList() },
    private val documentContentSearch: DocumentContentSearch = DocumentContentSearch { _, _ -> emptyList() },
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchNotesAndFoldersLocally(query: String): Flow<List<SearchItem>> {
        val workspaceFlow = authRepository.listenForWorkspace()

        if (query.isEmpty()) {
            return workspaceFlow.flatMapLatest { workspace ->
                flow { emit(getNotesAndFolders(workspace.id)) }
            }
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

        val presentationsFlow: Flow<List<SearchItem.PresentationInfo>> =
            workspaceFlow.flatMapLatest { workspace ->
                flow { emit(presentationSearch.search(query, workspace.id)) }
            }

        // The documents with the words inside them, after the ones found by title.
        val contentFlow: Flow<List<SearchItem.DocumentInfo>> =
            workspaceFlow.flatMapLatest { workspace ->
                flow { emit(documentContentSearch.search(query, workspace.id)) }
            }

        return combine(
            foldersFlow,
            documentsFlow,
            presentationsFlow,
            contentFlow
        ) { folders, documents, presentations, byContent ->
            mergeSearchItems((folders + documents).toSearchItems() + presentations, byContent)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchNotesAndFoldersRemotely(query: String): Flow<List<SearchItem>> {
        if (query.isEmpty()) {
            return flow { emit(emptyList()) }
        }

        return authRepository.listenForWorkspace().flatMapLatest { workspace ->
            flow {
                val results = searchApi.searchApi(query, workspace.id)
                // A document found by its words comes with the matching step as its only content.
                val documents = results.documents.map { document ->
                    SearchItem.DocumentInfo(
                        id = document.id,
                        label = document.title,
                        snippet = document.content.values.firstOrNull()?.text?.takeIf { it.isNotBlank() }
                    )
                }.take(RESULTS_LIMIT)
                val presentations = results.presentations.map { presentation ->
                    SearchItem.PresentationInfo(
                        id = presentation.id,
                        label = presentation.title,
                        documentId = presentation.documentId
                    )
                }
                emit(documents + presentations)
            }
        }
    }

    private suspend fun getNotesAndFolders(workspaceId: String): List<SearchItem> {
        val folders = folderSearch.getLastUpdated()
        val documents = documentSearch.getLastUpdatedAt(workspaceId)

        return (folders + documents).toSearchItems()
    }

    companion object {
        const val RESULTS_LIMIT = 12
    }
}

private fun List<MenuItem>.toSearchItems(): List<SearchItem> =
    this.map { menuItem ->
        when (menuItem) {
            is Folder -> SearchItem.FolderInfo(menuItem.id, menuItem.title)

            else -> SearchItem.DocumentInfo(menuItem.id, menuItem.title)
        }
    }.take(SearchRepository.RESULTS_LIMIT)

/**
 * [first] then [second] without repeating an id. When the same document is in both, the copy
 * with a snippet wins wherever it came from: a document found by its title here and by its words
 * on the backend shows the words it was found by.
 */
fun mergeSearchItems(first: List<SearchItem>, second: List<SearchItem>): List<SearchItem> {
    val snippets = (first + second)
        .filterIsInstance<SearchItem.DocumentInfo>()
        .filter { item -> item.snippet != null }
        .associateBy { item -> item.id }

    return (first + second)
        .distinctBy { item -> item.id }
        .map { item -> if (item is SearchItem.DocumentInfo) snippets[item.id] ?: item else item }
}

sealed interface SearchItem {
    val id: String

    data class FolderInfo(override val id: String, val label: String) : SearchItem

    /**
     * A document. [snippet] is the text of the step the query was found in, when the document
     * was found by its words rather than by its title.
     */
    data class DocumentInfo(
        override val id: String,
        val label: String,
        val snippet: String? = null
    ) : SearchItem

    /** A presentation, opened over the document it was made from. */
    data class PresentationInfo(
        override val id: String,
        val label: String,
        val documentId: String
    ) : SearchItem
}
