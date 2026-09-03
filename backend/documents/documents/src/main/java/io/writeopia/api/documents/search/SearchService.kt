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
import io.writeopia.sql.WriteopiaDbBackend

object SearchDocument {

    suspend fun search(
        query: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): ResultData<List<Document>> {
        return semanticSearch(query, workspaceId).mapSuspend { idList ->
            idList.mapNotNull { id ->
                writeopiaDb.getDocumentById(id, workspaceId)
            }
        }
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
