package io.writeopia.sqldelight.dao

import app.cash.sqldelight.async.coroutines.awaitAsList
import io.writeopia.app.sql.StoryStepEntityFtsQueries
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.fts.FtsQuery

/**
 * The full-text index of the story steps (`storyStepEntityFts`), kept complete by the triggers
 * on `storyStepEntity`: nothing writes to it from here.
 */
class StoryStepFtsSqlDelightDao(writeopiaDb: WriteopiaDb?) {

    private val storyStepEntityQueries: StoryStepEntityFtsQueries? =
        writeopiaDb?.storyStepEntityFtsQueries

    fun searchInDocument(query: String, documentId: String): Set<String> {
        val match = FtsQuery.fts4Match(query) ?: return emptySet()

        return storyStepEntityQueries?.searchFts(match)
            ?.executeAsList()
            ?.filter { (_, documentIdFts) -> documentIdFts == documentId }
            ?.mapNotNullTo(mutableSetOf()) { (position, _) -> position }
            ?: emptySet()
    }

    /**
     * The documents of [workspaceId] with a step whose text holds every word of [query] (by
     * prefix), one match per document: the first matching step in the document. The text
     * compared is the step's current one, so a step edited since it was indexed only counts
     * while it still matches.
     */
    suspend fun searchContent(query: String, workspaceId: String, limit: Int = DEFAULT_LIMIT): List<ContentMatch> {
        val match = FtsQuery.fts4Match(query) ?: return emptyList()

        return storyStepEntityQueries?.searchContent(match, workspaceId)
            ?.awaitAsList()
            .orEmpty()
            .asSequence()
            .filter { row -> row.text != null && FtsQuery.matches(row.text, query) }
            .distinctBy { row -> row.document_id }
            .take(limit)
            .map { row -> ContentMatch(row.document_id, row.title, row.text!!, row.position) }
            .toList()
    }

    /** A step whose text matched: the document it belongs to, and enough of the step to show it. */
    data class ContentMatch(
        val documentId: String,
        val title: String,
        val text: String,
        val position: String,
    )

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
