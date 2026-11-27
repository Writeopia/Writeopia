package io.writeopia.desktop

import io.writeopia.claudecode.LiveSearch
import io.writeopia.claudecode.mcp.SearchMatch
import io.writeopia.features.search.di.KmpSearchInjection
import io.writeopia.features.search.repository.SearchItem
import io.writeopia.features.search.repository.mergeSearchItems
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * What the global search of the app finds, for Claude Code: the documents on this computer (by
 * title, then by the words inside them, from the full-text index) and the backend's, merged like
 * the search dialog does. The folder of each document comes from the database; a document only
 * the backend has keeps the one the backend sent, if any.
 */
class AppLiveSearch(private val workspaceId: suspend () -> String) : LiveSearch {

    override suspend fun search(query: String): List<SearchMatch> = coroutineScope {
        // Built when used: the database is up by then.
        val repository = KmpSearchInjection.singleton().provideRepository()

        // The backend answers with nothing when it can't be reached; a slow one is not waited for.
        val remote = async {
            withTimeoutOrNull(REMOTE_TIMEOUT) { repository.searchNotesAndFoldersRemotely(query).first() }.orEmpty()
        }
        val local = repository.searchNotesAndFoldersLocally(query).first()

        val found = mergeSearchItems(local, remote.await()).filterIsInstance<SearchItem.DocumentInfo>()
        if (found.isEmpty()) return@coroutineScope emptyList()

        val stored = runCatching {
            RepositoryInjector.singleton().provideDocumentRepository()
                .loadDocumentByIds(found.map { it.id }, workspaceId())
        }.getOrDefault(emptyList()).associateBy { it.id }

        found.map { item ->
            val document = stored[item.id]
            SearchMatch(
                documentId = item.id,
                title = document?.title?.takeIf { it.isNotBlank() } ?: item.label,
                parentId = document?.parentId,
                text = item.snippet,
            )
        }
    }

    private companion object {
        val REMOTE_TIMEOUT = 8.seconds
    }
}
