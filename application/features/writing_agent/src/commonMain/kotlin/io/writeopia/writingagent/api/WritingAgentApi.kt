package io.writeopia.writingagent.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import io.writeopia.app.dto.writingagent.WritingSuggestionsResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.utils.ResultData
import kotlin.coroutines.cancellation.CancellationException

/** Asks the backend which actions the writer wants. The backend holds the TypeSafe key. */
interface WritingAgentApi {
    suspend fun suggestions(request: WritingSuggestionsRequest): ResultData<WritingSuggestionsResponse>
}

class KtorWritingAgentApi(
    private val client: HttpClient,
    private val baseUrl: String,
) : WritingAgentApi {

    override suspend fun suggestions(
        request: WritingSuggestionsRequest
    ): ResultData<WritingSuggestionsResponse> = try {
        val response = client.post("$baseUrl/${EndPoints.aiWritingSuggestions()}") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (response.status.value in 200..299) {
            ResultData.Complete(response.body())
        } else {
            ResultData.Error(Exception("Writing suggestions answered ${response.status.value}"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }
}
