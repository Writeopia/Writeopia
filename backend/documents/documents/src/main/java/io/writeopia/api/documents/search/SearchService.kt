package io.writeopia.api.documents.search

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.connection.ResultData
import io.writeopia.connection.Urls
import io.writeopia.connection.logger
import io.writeopia.connection.mapSuspend
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Instant

object SearchDocument {

    suspend fun search(
        query: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): ResultData<List<Document>> {
        // If AI Hub is configured, use semantic search
        if (Urls.AI_HUB != null) {
            return semanticSearch(query, workspaceId).mapSuspend { idList ->
                idList.mapNotNull { id ->
                    writeopiaDb.getDocumentById(id, workspaceId)
                }
            }
        }

        // Fallback to database title search
        logger.info("Using database title search for query: '$query'")
        return databaseSearch(query, workspaceId, writeopiaDb)
    }

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
