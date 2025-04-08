package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.writeopia.sdk.models.document.Document
import kotlinx.serialization.json.Json

class DocumentsApi(
    private val client: HttpClient,
    private val json: Json
) {

    // Todo: Implement!
    suspend fun getNewDocuments(): List<Document> = emptyList()

    suspend fun sendDocuments(documents: List<Document>) {
        // Todo: Implemented
    }
}

