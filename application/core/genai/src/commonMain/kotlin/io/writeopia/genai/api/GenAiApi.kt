package io.writeopia.genai.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.genai.model.GenAiRequest
import io.writeopia.genai.model.GenAiResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

class GenAiApi(
    private val client: HttpClient,
    private val json: Json,
    private val baseUrl: String,
    private val getAuthToken: suspend () -> String?
) {
    private val generateMutex = Mutex()

    suspend fun checkStatus(): ResultData<Boolean> = try {
        val response = client.get("$baseUrl/${EndPoints.aiStatus()}") {
            contentType(ContentType.Application.Json)
            getAuthToken()?.let { token ->
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        val body = response.body<Map<String, Boolean>>()
        ResultData.Complete(body["available"] == true)
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    suspend fun generate(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiGenerate()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamGenerate(prompt: String, model: String? = null): Flow<ResultData<String>> =
        streamFromEndpoint(EndPoints.aiGenerate(), prompt, model)

    suspend fun generateSummary(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiSummary()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamSummary(prompt: String, model: String? = null): Flow<ResultData<String>> =
        streamFromEndpoint(EndPoints.aiSummary(), prompt, model)

    suspend fun generateActionPoints(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiActionPoints()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamActionPoints(prompt: String, model: String? = null): Flow<ResultData<String>> =
        streamFromEndpoint(EndPoints.aiActionPoints(), prompt, model)

    suspend fun generateFaq(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiFaq()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamFaq(prompt: String, model: String? = null): Flow<ResultData<String>> =
        streamFromEndpoint(EndPoints.aiFaq(), prompt, model)

    suspend fun generateTags(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiTags()}") {
                    contentType(ContentType.Application.Json)
                    getAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamTags(prompt: String, model: String? = null): Flow<ResultData<String>> =
        streamFromEndpoint(EndPoints.aiTags(), prompt, model)

    /**
     * Shared streaming implementation that properly handles Flow exception transparency.
     * Buffers results inside execute callback and emits after execute returns to avoid
     * calling emit from within a non-suspending callback context.
     */
    private fun streamFromEndpoint(
        endpoint: String,
        prompt: String,
        model: String?
    ): Flow<ResultData<String>> = flow<ResultData<String>> {
        val bufferedResults = mutableListOf<ResultData<String>>()

        client.preparePost {
            url("$baseUrl/$endpoint")
            contentType(ContentType.Application.Json)
            getAuthToken()?.let { token ->
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(GenAiRequest(prompt, model, stream = true))
        }.execute { response ->
            val channel = response.body<ByteReadChannel>()

            while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                val line = channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                // SSE format: "data: {...}"
                val jsonData = if (line.startsWith("data: ")) {
                    line.removePrefix("data: ")
                } else {
                    continue
                }

                val parsed: GenAiResponse = json.decodeFromString(jsonData)

                if (parsed.error?.isNotEmpty() == true) {
                    throw GenAiException(parsed.error)
                }

                if (parsed.response != null) {
                    bufferedResults.add(ResultData.Complete(parsed.response))
                }

                if (parsed.done) {
                    break
                }
            }
        }

        // Emit buffered results after execute returns
        for (result in bufferedResults) {
            emit(result)
        }
    }.catch { e ->
        // Don't convert cancellation exceptions to errors - let them propagate
        if (e is CancellationException) throw e
        emit(ResultData.Error<String>(e.toException()))
    }
}

/**
 * Exception for GenAI API errors returned in the response body.
 */
class GenAiException(message: String) : Exception(message)

/**
 * Converts a Throwable to an Exception for use with ResultData.Error.
 */
private fun Throwable.toException(): Exception =
    this as? Exception ?: Exception(this.message, this)
