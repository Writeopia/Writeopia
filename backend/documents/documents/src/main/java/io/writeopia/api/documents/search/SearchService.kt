package io.writeopia.api.documents.search

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.api.documents.documents.repository.storyStepSearch
import io.writeopia.connection.ResultData
import io.writeopia.connection.Urls
import io.writeopia.connection.logger
import io.writeopia.connection.mapSuspend
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.databse.search.StoryStepSearchStore
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Instant

object SearchDocument {

    /**
     * The documents of [workspaceId] matching [query]: by title (or by meaning, through the AI Hub
     * when it is configured), then by the words inside them (full-text search over the story
     * steps). A document found by its content carries the matching step as its only content, so
     * the clients can show a snippet and open the document at it; a document found by title alone
     * has no content.
     */
    suspend fun search(
        query: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): ResultData<List<Document>> {
        val byTitle = if (Urls.AI_HUB != null) {
            semanticSearch(query, workspaceId).mapSuspend { idList ->
                idList.mapNotNull { id ->
                    writeopiaDb.getDocumentById(id, workspaceId)
                }
            }
        } else {
            // Fallback to database title search
            logger.info("Using database title search for query: '$query'")
            databaseSearch(query, workspaceId, writeopiaDb)
        }

        return byTitle.mapSuspend { documents -> withContentMatches(documents, query, workspaceId, writeopiaDb) }
    }

    /** [documents] plus the ones found by their content, each content match attached as a snippet step. */
    private suspend fun withContentMatches(
        documents: List<Document>,
        query: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<Document> {
        val matches = try {
            writeopiaDb.storyStepSearch().search(workspaceId, query)
        } catch (e: Exception) {
            logger.error("Content search failed for query: '$query'", e)
            emptyList()
        }
        if (matches.isEmpty()) return documents

        val matchByDocument = matches.associateBy { match -> match.documentId }
        val withSnippets = documents.map { document ->
            matchByDocument[document.id]?.let { match -> document.withSnippet(match) } ?: document
        }
        val found = documents.mapTo(mutableSetOf()) { document -> document.id }
        val byContent = matches
            .filter { match -> match.documentId !in found }
            .mapNotNull { match ->
                writeopiaDb.getDocumentById(match.documentId, workspaceId)?.withSnippet(match)
            }

        logger.info("Content search returned ${matches.size} matches, ${byContent.size} new documents")
        return withSnippets + byContent
    }

    private fun Document.withSnippet(match: StoryStepSearchStore.ContentMatch): Document =
        copy(
            content = mapOf(
                match.position to StoryStep(
                    id = match.stepId,
                    type = StoryTypes.entries.firstOrNull { it.type.number == match.type }?.type
                        ?: StoryTypes.TEXT.type,
                    text = match.text,
                )
            )
        )

    private fun databaseSearch(
        query: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): ResultData<List<Document>> {
        val documents = writeopiaDb.documentEntityQueries.query(query, workspaceId)
            .executeAsList()
            .map { entity ->
                Document(
                    id = entity.id,
                    title = entity.title,
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                    lastSyncedAt = Instant.fromEpochMilliseconds(entity.last_synced),
                    workspaceId = entity.workspace_id,
                    favorite = entity.favorite,
                    parentId = entity.parent_document_id,
                    icon = entity.icon?.let { MenuItem.Icon(it, entity.icon_tint) },
                    isLocked = entity.is_locked,
                )
            }
        logger.info("Database search returned ${documents.size} documents")
        return ResultData.Complete(documents)
    }

    private suspend fun semanticSearch(query: String, workspaceId: String): ResultData<List<String>> {
        val aiHubUrl = Urls.AI_HUB
        if (aiHubUrl == null) {
            logger.info("Semantic search skipped - AI Hub is not configured")
            return ResultData.Complete(emptyList())
        }

        logger.info("Semantic search - calling AI Hub at $aiHubUrl for query: '$query'")
        val request = wrWebClient.get("$aiHubUrl/documents/search/?q=${query}") {
            contentType(ContentType.Application.Json)
        }

        return if (request.status.isSuccess()) {
            val ids: List<String> = request.body()
            logger.info("Semantic search - AI Hub returned ${ids.size} document IDs")
            ResultData.Complete(ids)
        } else {
            logger.warn("Semantic search - AI Hub returned error status: ${request.status}")
            ResultData.Complete(emptyList())
        }
    }
}
