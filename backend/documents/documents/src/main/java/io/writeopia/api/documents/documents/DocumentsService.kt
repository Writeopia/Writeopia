package io.writeopia.api.documents.documents

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.api.documents.documents.dto.SendDocumentsRequest
import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.api.documents.search.SearchDocument
import io.writeopia.connection.ResultData
import io.writeopia.connection.Urls
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sql.WriteopiaDbBackend

object DocumentsService {

    suspend fun receiveDocuments(
        documents: List<Document>,
        writeopiaDb: WriteopiaDbBackend,
        useAi: Boolean
    ): Boolean {
        documents.forEach { document ->
            writeopiaDb.saveDocument(document)
        }

        return if (useAi) sendToAiHub(documents) else true
    }

    suspend fun search(
        query: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend
    ): ResultData<List<Document>> =
        SearchDocument.search(query, userId, writeopiaDb)

    suspend fun getDocumentById(id: String, writeopiaDb: WriteopiaDbBackend): Document? =
        writeopiaDb.getDocumentById(id)

    private suspend fun sendToAiHub(documents: List<Document>) =
        wrWebClient.post("${Urls.AI_HUB}/documents/") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documents.map { it.toApi() }))
        }.status
            .isSuccess()
}
