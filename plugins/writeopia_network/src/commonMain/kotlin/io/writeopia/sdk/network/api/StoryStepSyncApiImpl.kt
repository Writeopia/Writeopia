package io.writeopia.sdk.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse

/**
 * HTTP implementation of [StoryStepSyncApi].
 *
 * @param client The Ktor HTTP client to use for requests.
 * @param baseUrl The base URL of the backend API.
 */
class StoryStepSyncApiImpl(
    private val client: HttpClient,
    private val baseUrl: String
) : StoryStepSyncApi {

    override suspend fun syncStorySteps(request: StoryStepSyncRequest): StoryStepSyncResponse =
        client.post(
            "$baseUrl/api/docs/workspace/${request.workspaceId}/document/${request.documentId}/steps/sync"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
