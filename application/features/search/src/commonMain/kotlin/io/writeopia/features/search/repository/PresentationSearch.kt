package io.writeopia.features.search.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import io.writeopia.app.sql.PresentationEntityQueries

/** Finds the presentations of a workspace by their title. */
fun interface PresentationSearch {
    suspend fun search(query: String, workspaceId: String): List<SearchItem.PresentationInfo>
}

/**
 * The presentations kept in the app database, the ones the local AI made. The queries are null
 * where the database doesn't exist (Android), and then nothing is found.
 */
class PresentationSqlSearch(private val queries: PresentationEntityQueries?) : PresentationSearch {

    override suspend fun search(query: String, workspaceId: String): List<SearchItem.PresentationInfo> =
        queries?.search(query, workspaceId)?.awaitAsList().orEmpty().map { entity ->
            SearchItem.PresentationInfo(
                id = entity.id,
                label = entity.title,
                documentId = entity.document_id
            )
        }
}
