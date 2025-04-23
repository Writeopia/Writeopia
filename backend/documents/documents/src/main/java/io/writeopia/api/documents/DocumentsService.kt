package io.writeopia.api.documents

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.api.documents.dto.SendDocumentsRequest
import io.writeopia.api.documents.repository.saveDocument
import io.writeopia.connection.Urls
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sql.WriteopiaDbBackend

object DocumentsService {

    suspend fun receiveDocuments(
        documents: List<Document>,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        documents.forEach { document ->
            writeopiaDb.saveDocument(document)
        }

        return sendToAiHub(documents)
    }

    private suspend fun sendToAiHub(documents: List<Document>) =
        wrWebClient.post("${Urls.aiHub}/documents") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documents.map { it.toApi() }))
        }.status
            .isSuccess()
}
