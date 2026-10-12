package io.writeopia.api.ai.folders

import io.writeopia.api.embeddings.DocumentPlainText
import io.writeopia.api.embeddings.EmbeddingPurpose
import io.writeopia.api.embeddings.Embeddings
import io.writeopia.app.dto.writingagent.FolderCandidate
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import io.writeopia.connection.logger
import io.writeopia.sql.Folder_entity
import io.writeopia.sql.WriteopiaDbBackend

/**
 * Picks the folders Jev is asked about for a document, and profiles them.
 *
 * With embeddings, the document is embedded as a query and the folders of the workspace are
 * ranked by how close their vectors are (the mean of their documents, or their title when
 * empty). Folders without a vector yet fill the remaining places by recent activity. Without
 * embeddings, or when the model does not answer, the most recently updated folders are taken.
 * Without a workspace, the folders the app sent are kept, as before.
 *
 * Every candidate gets its profile: the path of folders above it, the titles of its most recent
 * documents and the tags those carry, read from the database.
 */
class FolderCandidateRanker(
    private val embeddings: Embeddings?,
    private val limit: Int = FolderSuggestionsRequest.MAX_FOLDERS,
    private val recentTitlesLimit: Int = DEFAULT_RECENT_TITLES,
    private val tagsLimit: Int = DEFAULT_TAGS,
    private val maxPathDepth: Int = DEFAULT_PATH_DEPTH,
) {

    /** Where the candidates came from, for the log. */
    enum class Source { APP, VECTORS, ACTIVITY, NONE }

    data class Outcome(val candidates: List<FolderCandidate>, val source: Source)

    suspend fun candidates(request: FolderSuggestionsRequest, db: WriteopiaDbBackend?): Outcome {
        val workspaceId = request.workspaceId
        if (workspaceId.isBlank() || db == null) {
            return Outcome(request.folders.take(limit), Source.APP)
        }

        val (ids, source) = rankedIds(request, workspaceId, db)
        if (ids.isEmpty()) {
            // The workspace has no folders the backend knows: whatever the app offered.
            return if (request.folders.isEmpty()) Outcome(emptyList(), Source.NONE) else Outcome(request.folders.take(limit), Source.APP)
        }

        val rows = db.folderEntityQueries.selectByIds(ids, workspaceId).executeAsList().associateBy { row -> row.id }
        val candidates = ids.mapNotNull { id -> rows[id] }.map { row -> profile(row, workspaceId, db) }

        return Outcome(candidates, source)
    }

    private suspend fun rankedIds(
        request: FolderSuggestionsRequest,
        workspaceId: String,
        db: WriteopiaDbBackend,
    ): Pair<List<String>, Source> {
        val byActivity = byActivity(workspaceId, db)
        val embeddings = embeddings ?: return byActivity to Source.ACTIVITY

        val query = embeddings.embedder.embed(
            DocumentPlainText.embeddingInput(request.documentTitle, request.documentText),
            EmbeddingPurpose.QUERY,
        )
        if (query == null) {
            logger.warn("Folder ranking: the document could not be embedded, ranking folders by activity")
            return byActivity to Source.ACTIVITY
        }

        val ranked = embeddings.store.rankFolders(workspaceId, query, embeddings.embedder.model, limit)
            .map { ranked -> ranked.folderId }
        if (ranked.isEmpty()) return byActivity to Source.ACTIVITY

        // Folders without a vector yet take the places left.
        val filled = (ranked + byActivity.filter { id -> id !in ranked }).take(limit)
        return filled to Source.VECTORS
    }

    /** The folders of the workspace with a name, most recently updated first. */
    private fun byActivity(workspaceId: String, db: WriteopiaDbBackend): List<String> =
        db.folderEntityQueries.selectByWorkspace(workspaceId)
            .executeAsList()
            .filter { row -> row.title.isNotBlank() }
            .sortedByDescending { row -> row.last_updated_at ?: 0L }
            .take(limit)
            .map { row -> row.id }

    private fun profile(row: Folder_entity, workspaceId: String, db: WriteopiaDbBackend): FolderCandidate {
        val recent = db.documentEntityQueries
            .selectRecentTitlesByParentId(row.id, workspaceId, recentTitlesLimit.toLong())
            .executeAsList()

        val tags = if (recent.isEmpty()) {
            emptyList()
        } else {
            val tagIds = db.documentTagLinkEntityQueries.selectByDocumentIds(recent.map { it.id })
                .executeAsList()
                .map { link -> link.tag_id }
                .distinct()
            if (tagIds.isEmpty()) {
                emptyList()
            } else {
                db.documentTagEntityQueries.selectByIds(tagIds)
                    .executeAsList()
                    .filter { tag -> !tag.deleted }
                    .map { tag -> tag.name }
                    .distinct()
                    .take(tagsLimit)
            }
        }

        return FolderCandidate(
            id = row.id,
            name = row.title,
            path = pathOf(row, workspaceId, db),
            recentTitles = recent.map { document -> document.title }.filter { title -> title.isNotBlank() },
            tags = tags,
        )
    }

    /** The titles of the folders above [row], root first, joined by " / ". Stops at the root, a gap or a cycle. */
    private fun pathOf(row: Folder_entity, workspaceId: String, db: WriteopiaDbBackend): String {
        val titles = ArrayDeque<String>()
        val visited = mutableSetOf(row.id)
        var parentId = row.parent_id

        while (parentId.isNotBlank() && parentId != ROOT && visited.add(parentId) && titles.size < maxPathDepth) {
            val parent = db.folderEntityQueries.selectFolderById(parentId, workspaceId).executeAsOneOrNull() ?: break
            titles.addFirst(parent.title)
            parentId = parent.parent_id
        }

        return titles.joinToString(separator = " / ")
    }

    companion object {
        const val DEFAULT_RECENT_TITLES = 5
        const val DEFAULT_TAGS = 5
        const val DEFAULT_PATH_DEPTH = 6
        private const val ROOT = "root"
    }
}
