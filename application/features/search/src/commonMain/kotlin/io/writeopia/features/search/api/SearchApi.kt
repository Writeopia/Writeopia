package io.writeopia.features.search.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toModel

class SearchApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun searchApi(query: String): List<Document> {
        return try {
            val url = "$baseUrl/api/document/search?q=\"$query\""
            val request = client.get(url) {
                contentType(ContentType.Application.Json)
            }

            if (request.status.isSuccess()) {
                request.body<List<DocumentApi>>().map { it.toModel() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
