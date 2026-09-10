package io.writeopia.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.response.LocalAiAutoConfigResponse
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CancellationException

/**
 * Fetches the public, CDN-cached configuration used to auto-configure Local AI (Ollama / llmman).
 */
class LocalAiAutoConfigApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    suspend fun getAutoConfig(): ResultData<LocalAiAutoConfigResponse> =
        try {
            val response: LocalAiAutoConfigResponse = client.get("$baseUrl/${EndPoints.aiLocalConfig()}").body()

            // Validate that defaultTierIndex is within valid range
            if (response.defaultTierIndex !in response.modelTiers.indices) {
                return ResultData.Error(
                    IllegalStateException(
                        "Invalid defaultTierIndex: ${response.defaultTierIndex} " +
                            "(valid range: 0..${response.modelTiers.size - 1})"
                    )
                )
            }

            ResultData.Complete(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultData.Error(e)
        }
}
