package io.writeopia.core.presentations.api

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.core.presentations.PresentationException
import io.writeopia.core.presentations.PresentationGenerator
import io.writeopia.core.presentations.PresentationsRepository
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.data.PresentationApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sdk.serialization.request.GeneratePresentationRequest
import io.writeopia.sdk.serialization.response.GeneratePresentationResponse
import io.writeopia.sdk.serialization.response.PresentationsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * The presentations of an online workspace: the backend asks the AI, parses the slides and
 * keeps them, so the app only shows what comes back.
 */
class PresentationsApi(
    private val clientProvider: () -> HttpClient,
    private val baseUrl: String,
    private val json: Json = writeopiaJson
) : PresentationsRepository, PresentationGenerator {

    constructor(client: HttpClient, baseUrl: String) : this({ client }, baseUrl)

    private val client: HttpClient get() = clientProvider()

    override suspend fun presentations(documentId: String, workspaceId: String): ResultData<List<Presentation>> =
        request {
            val response = client.get("$baseUrl/${EndPoints.documentPresentations(workspaceId, documentId)}")
            if (!response.status.isSuccess()) throw PresentationException(errorMessage(response.status, response.bodyAsText()))
            json.decodeFromString<PresentationsResponse>(response.bodyAsText())
                .presentations
                .map { it.toModel(workspaceId) }
        }

    override suspend fun presentation(id: String, workspaceId: String): ResultData<Presentation?> =
        request {
            val response = client.get("$baseUrl/${EndPoints.presentation(workspaceId, id)}")
            when {
                response.status == HttpStatusCode.NotFound -> null
                !response.status.isSuccess() -> throw PresentationException(errorMessage(response.status, response.bodyAsText()))
                else -> json.decodeFromString<PresentationApi>(response.bodyAsText()).toModel(workspaceId)
            }
        }

    override suspend fun deletePresentation(id: String, workspaceId: String): ResultData<Unit> =
        request {
            val response = client.delete("$baseUrl/${EndPoints.presentation(workspaceId, id)}")
            if (!response.status.isSuccess()) throw PresentationException(errorMessage(response.status, response.bodyAsText()))
        }

    override suspend fun generatePresentation(documentId: String, workspaceId: String): ResultData<Presentation> =
        request {
            val response = client.post("$baseUrl/${EndPoints.documentPresentations(workspaceId, documentId)}") {
                contentType(ContentType.Application.Json)
                // Encoded here, so the call doesn't depend on the plugins of the client.
                setBody(json.encodeToString(GeneratePresentationRequest.serializer(), GeneratePresentationRequest()))
            }
            val body = runCatching { json.decodeFromString<GeneratePresentationResponse>(response.bodyAsText()) }.getOrNull()
            val presentation = body?.presentation
            if (response.status.isSuccess() && presentation != null) {
                presentation.toModel(workspaceId)
            } else {
                throw PresentationException(body?.error ?: errorMessage(response.status, ""))
            }
        }

    private fun errorMessage(status: HttpStatusCode, body: String): String =
        when (status) {
            HttpStatusCode.Forbidden -> "Presentations need a premium account in an online workspace."
            HttpStatusCode.ServiceUnavailable -> "The cloud AI is not available right now."
            HttpStatusCode.NotFound -> "The document was not found on the server. Sync it and try again."
            else -> body.ifBlank { "Request failed with status $status" }
        }

    private inline fun <T> request(block: () -> T): ResultData<T> =
        try {
            ResultData.Complete(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultData.Error(e)
        }
}
