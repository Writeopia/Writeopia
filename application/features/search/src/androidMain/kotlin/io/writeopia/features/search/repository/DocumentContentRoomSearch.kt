package io.writeopia.features.search.repository

import io.writeopia.sdk.persistence.dao.StoryStepFtsDao
import io.writeopia.sqldelight.fts.FtsQuery

/**
 * The full-text index of the story steps in the Room database, the one Android writes through.
 * The same words and the same rules as the SQLDelight search of the other platforms: every
 * word required, by prefix, the text compared as it is now.
 */
class DocumentContentRoomSearch(private val dao: StoryStepFtsDao) : DocumentContentSearch {

    override suspend fun search(query: String, workspaceId: String): List<SearchItem.DocumentInfo> {
        val match = FtsQuery.fts4Match(query) ?: return emptyList()

        return dao.searchContent(match, workspaceId)
            .asSequence()
            .mapNotNull { row -> row.text?.let { text -> row to text } }
            .filter { (_, text) -> FtsQuery.matches(text, query) }
            .distinctBy { (row, _) -> row.documentId }
            .take(LIMIT)
            .map { (row, text) -> SearchItem.DocumentInfo(id = row.documentId, label = row.title, snippet = text) }
            .toList()
    }

    companion object {
        const val LIMIT = 20
    }
}
