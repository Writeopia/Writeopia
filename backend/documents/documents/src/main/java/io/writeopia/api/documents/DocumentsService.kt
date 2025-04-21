package io.writeopia.api.documents

import io.ktor.client.request.post
import io.writeopia.api.documents.repository.saveDocument
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sql.WriteopiaDbBackend

object DocumentsService {

    suspend fun receiveDocuments(documents: List<Document>, writeopiaDb: WriteopiaDbBackend) {
        documents.forEach { document ->
            writeopiaDb.saveDocument(document)
        }
    }

    private fun sendToAiHub(documents: List<Document>) {
        wrWebClient.post()
    }
}
