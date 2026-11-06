package io.writeopia.features.search.repository

import io.writeopia.sqldelight.dao.StoryStepFtsSqlDelightDao

/** Finds the documents of a workspace by the words inside them, each with the step that matched. */
fun interface DocumentContentSearch {
    suspend fun search(query: String, workspaceId: String): List<SearchItem.DocumentInfo>
}

/**
 * The full-text index of the story steps in the app database, the one the in-document search
 * uses. The DAO is null where the database doesn't exist (Android), and then nothing is found:
 * there the content matches come from the backend alone.
 */
class DocumentContentSqlSearch(private val dao: StoryStepFtsSqlDelightDao?) : DocumentContentSearch {

    override suspend fun search(query: String, workspaceId: String): List<SearchItem.DocumentInfo> =
        dao?.searchContent(query, workspaceId).orEmpty().map { match ->
            SearchItem.DocumentInfo(id = match.documentId, label = match.title, snippet = match.text)
        }
}
