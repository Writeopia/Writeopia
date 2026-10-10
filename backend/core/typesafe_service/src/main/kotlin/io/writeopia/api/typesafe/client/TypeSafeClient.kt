package io.writeopia.api.typesafe.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.writeopia.api.typesafe.model.SystemOneQuestion
import io.writeopia.api.typesafe.model.SystemOneRequest
import io.writeopia.api.typesafe.model.SystemOneResponse
import io.writeopia.connection.logger
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.cancellation.CancellationException

sealed class TypeSafeResult {
    data class Success(val response: SystemOneResponse) : TypeSafeResult()

    data class Failure(val message: String) : TypeSafeResult()
}

/**
 * Talks to TypeSafe's System One API with Jev. The API key stays on the backend; the apps never
 * see it. Overloads (429 and 529) are retried with exponential backoff, as the docs recommend.
 */
class TypeSafeClient(
    private val apiKey: String? = System.getenv("TYPESAFE_API_KEY"),
    private val baseUrl: String = System.getenv("TYPESAFE_BASE_URL") ?: DEFAULT_BASE_URL,
    private val model: String = System.getenv("TYPESAFE_MODEL") ?: DEFAULT_MODEL,
    private val maxRetries: Int = 2,
    private val retryDelayMillis: Long = 500,
    engine: HttpClientEngine? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: HttpClient by lazy {
        val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) {
                json(json)
            }
        }
        if (engine != null) HttpClient(engine, config) else HttpClient(CIO, config)
    }

    fun isAvailable(): Boolean = !apiKey.isNullOrBlank()

    /**
     * Asks every question in [questions] about [state] in one request. The questions run in
     * parallel on TypeSafe's side, so asking more barely changes the latency.
     */
    suspend fun ask(state: JsonElement, questions: Map<String, SystemOneQuestion>): TypeSafeResult {
        val key = apiKey?.takeIf { it.isNotBlank() }
            ?: return TypeSafeResult.Failure("TypeSafe is not configured. Set TYPESAFE_API_KEY.")

        val request = SystemOneRequest(state = state, model = model, questions = questions)
        var attempt = 0

        while (true) {
            try {
                val response = httpClient.post("$baseUrl/v1/systemone") {
                    header(HttpHeaders.Authorization, "Bearer $key")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                when {
                    response.status.isSuccess() -> {
                        return TypeSafeResult.Success(response.body<SystemOneResponse>())
                    }

                    response.status.isRetryable() && attempt < maxRetries -> {
                        attempt++
                        val wait = retryDelayMillis * (1L shl (attempt - 1))
                        logger.warn("TypeSafe answered {}. Retrying in {} ms", response.status, wait)
                        delay(wait)
                    }

                    else -> {
                        val body = response.bodyAsText()
                        logger.error("TypeSafe request failed: {} {}", response.status, body)
                        return TypeSafeResult.Failure("TypeSafe answered ${response.status.value}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Error calling TypeSafe", e)
                return TypeSafeResult.Failure(e.message ?: "Unknown error calling TypeSafe")
            }
        }
    }

    private fun HttpStatusCode.isSuccess() = value in 200..299

    private fun HttpStatusCode.isRetryable() = value == 429 || value == 529

    companion object {
        const val DEFAULT_BASE_URL = "https://api.typesafe.ai"
        const val DEFAULT_MODEL = "jev-latest"
    }
}
