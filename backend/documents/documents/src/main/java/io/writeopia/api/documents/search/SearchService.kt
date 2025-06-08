package io.writeopia.api.documents.search

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.connection.ResultData
import io.writeopia.connection.Urls
import io.writeopia.connection.mapSuspend
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sql.WriteopiaDbBackend

object SearchDocument {

    suspend fun search(
        query: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend
    ): ResultData<List<Document>> {
        return semanticSearch(query, userId).mapSuspend { idList ->
            idList.mapNotNull { id ->
                writeopiaDb.getDocumentById(id)
            }
        }
    }

    private suspend fun semanticSearch(query: String, userId: String): ResultData<List<String>> {
        val request = wrWebClient.get("${Urls.AI_HUB}/documents/search/?q=${query}") {
            contentType(ContentType.Application.Json)
        }

        return if (request.status.isSuccess()) {
            ResultData.Complete(request.body())
        } else {
            ResultData.Complete(emptyList())
        }
    }
}
