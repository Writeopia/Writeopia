package io.writeopia.features.search.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.response.SearchResponse
import kotlinx.coroutines.CancellationException

/** What the backend finds in a workspace; the presentations come without their slides. */
data class SearchResults(
    val documents: List<Document> = emptyList(),
    val presentations: List<Presentation> = emptyList()
)

class SearchApi(private val client: HttpClient, private val baseUrl: String) {

    /** The documents and the presentations of the workspace, in one request. */
    suspend fun searchApi(query: String, workspaceId: String): SearchResults = try {
        val request = client.get("$baseUrl/api/docs/workspace/$workspaceId/document/search") {
            url {
                parameters.append("q", query)
            }
            contentType(ContentType.Application.Json)
        }

        if (request.status.isSuccess()) {
            val response = request.body<SearchResponse>()
            SearchResults(
                documents = response.documents.map { it.toModel() },
                presentations = response.presentations.map { it.toModel(workspaceId) }
            )
        } else {
            SearchResults()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SearchResults()
    }
}
