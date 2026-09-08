package io.writeopia.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.responses.LocalAiAutoConfigResponse
import io.writeopia.sdk.models.utils.ResultData

/**
 * Fetches the public, CDN-cached configuration used to auto-configure Local AI (Ollama / llmman).
 */
class LocalAiAutoConfigApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    suspend fun getAutoConfig(): ResultData<LocalAiAutoConfigResponse> =
        try {
            val response = client.get("$baseUrl/${EndPoints.aiLocalConfig()}")
            ResultData.Complete(response.body())
        } catch (e: Exception) {
            ResultData.Error(e)
        }
}
