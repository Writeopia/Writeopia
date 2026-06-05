package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.persistence.core.tracker.StoryStepSyncApi
import io.writeopia.sdk.persistence.core.tracker.StoryStepSyncResult
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse

/**
 * HTTP client implementation of StoryStepSyncApi that communicates with the backend.
 */
class StoryStepSyncApiImpl(
    private val client: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?
) : StoryStepSyncApi {

    override suspend fun syncStorySteps(
        documentId: String,
        workspaceId: String,
        lastSyncTimestamp: Long,
        modifiedSteps: List<Pair<StoryStep, Double>>,
        deletedStepIds: List<String>
    ): StoryStepSyncResult? {
        val token = tokenProvider() ?: return null

        val request = StoryStepSyncRequest(
            documentId = documentId,
            workspaceId = workspaceId,
            lastSyncTimestamp = lastSyncTimestamp,
            modifiedSteps = modifiedSteps.map { (step, position) ->
                step.toApi(position)
            },
            deletedStepIds = deletedStepIds
        )

        return try {
            val response = client.post(
                "$baseUrl/api/workspace/$workspaceId/document/$documentId/storysteps/sync"
            ) {
                contentType(ContentType.Application.Json)
                setBody(request)
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            if (response.status.isSuccess()) {
                val syncResponse = response.body<StoryStepSyncResponse>()
                StoryStepSyncResult(
                    serverTimestamp = syncResponse.serverTimestamp,
                    updatedSteps = syncResponse.updatedSteps.map { it.toModel() },
                    deletedStepIds = syncResponse.deletedStepIds
                )
            } else {
                println("StoryStep sync failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("StoryStep sync error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
