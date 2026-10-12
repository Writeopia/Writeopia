package io.writeopia.writingagent.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import io.writeopia.app.dto.writingagent.FolderSuggestionsResponse
import io.writeopia.app.dto.writingagent.TagCreateRequest
import io.writeopia.app.dto.writingagent.TagCreateResponse
import io.writeopia.app.dto.writingagent.TagSuggestionsRequest
import io.writeopia.app.dto.writingagent.TagSuggestionsResponse
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import io.writeopia.app.dto.writingagent.WritingSuggestionsResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.utils.ResultData
import kotlin.coroutines.cancellation.CancellationException

/** Asks the backend which actions the writer wants. The backend holds the TypeSafe key. */
interface WritingAgentApi {
    suspend fun suggestions(request: WritingSuggestionsRequest): ResultData<WritingSuggestionsResponse>

    /** Which of the workspace folders a document that sits in the root belongs to. */
    suspend fun folderSuggestions(request: FolderSuggestionsRequest): ResultData<FolderSuggestionsResponse>

    /** Which of the workspace tags a document should carry. */
    suspend fun tagSuggestions(request: TagSuggestionsRequest): ResultData<TagSuggestionsResponse>

    /** A new tag, name and explanation, made up by the cloud AI for a document. */
    suspend fun createTag(request: TagCreateRequest): ResultData<TagCreateResponse>
}

class KtorWritingAgentApi(
    private val client: HttpClient,
    private val baseUrl: String,
) : WritingAgentApi {

    override suspend fun suggestions(
        request: WritingSuggestionsRequest
    ): ResultData<WritingSuggestionsResponse> =
        post(EndPoints.aiWritingSuggestions(), request, "Writing suggestions")

    override suspend fun folderSuggestions(
        request: FolderSuggestionsRequest
    ): ResultData<FolderSuggestionsResponse> =
        post(EndPoints.aiFolderSuggestions(), request, "Folder suggestions")

    override suspend fun tagSuggestions(
        request: TagSuggestionsRequest
    ): ResultData<TagSuggestionsResponse> =
        post(EndPoints.aiTagSuggestions(), request, "Tag suggestions")

    override suspend fun createTag(request: TagCreateRequest): ResultData<TagCreateResponse> =
        post(EndPoints.aiTagCreate(), request, "Tag creation")

    private suspend inline fun <reified Request, reified Response> post(
        endpoint: String,
        request: Request,
        name: String,
    ): ResultData<Response> = try {
        val response = client.post("$baseUrl/$endpoint") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (response.status.value in 200..299) {
            ResultData.Complete(response.body())
        } else {
            ResultData.Error(Exception("$name answered ${response.status.value}"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }
}
