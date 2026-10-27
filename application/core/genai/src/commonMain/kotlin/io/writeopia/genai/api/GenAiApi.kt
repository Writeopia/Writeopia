package io.writeopia.genai.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.writeopia.app.dto.ai.AiCommandIntentResponse
import io.writeopia.app.dto.ai.AiCommandRequest
import io.writeopia.app.dto.ai.AiImageRequest
import io.writeopia.app.dto.ai.AiImageResponse
import io.writeopia.sdk.serialization.response.HeaderImageApi
import io.writeopia.sdk.serialization.request.HeaderImageGenerateRequest
import io.writeopia.sdk.serialization.response.HeaderImageGenerateResponse
import io.writeopia.sdk.serialization.request.HeaderImageSaveRequest
import io.writeopia.sdk.serialization.response.HeaderImagesResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.response.AiUsageResponse
import io.writeopia.genai.model.GenAiRequest
import io.writeopia.genai.model.GenAiResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

class GenAiApi(
    private val client: HttpClient,
    private val json: Json,
    private val baseUrl: String
) {
    private val generateMutex = Mutex()

    suspend fun checkStatus(): ResultData<Boolean> = try {
        val response = client.get("$baseUrl/${EndPoints.aiStatus()}") {
            contentType(ContentType.Application.Json)
        }
        val body = response.body<Map<String, Boolean>>()
        ResultData.Complete(body["available"] == true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    suspend fun getUsage(): ResultData<AiUsageResponse> = try {
        val response = client.get("$baseUrl/${EndPoints.aiUsage()}") {
            contentType(ContentType.Application.Json)
        }
        ResultData.Complete(response.body())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    suspend fun generate(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        generateMutex.withLock {
            try {
                val response = client.post("$baseUrl/${EndPoints.aiGenerate()}") {
                    contentType(ContentType.Application.Json)
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: CancellationException) {
                throw e
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
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: CancellationException) {
                throw e
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
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: CancellationException) {
                throw e
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
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: CancellationException) {
                throw e
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
                    setBody(GenAiRequest(prompt, model, stream = false))
                }
                ResultData.Complete(response.body())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ResultData.Error(e)
            }
        }

    fun streamTags(prompt: String, model: String? = null): Flow<ResultData<String>> =
        streamFromEndpoint(EndPoints.aiTags(), prompt, model)

    /**
     * Asks the backend what a free command does with the text it is about: replace it or add
     * to it. The answer is an error when the backend could not decide.
     */
    suspend fun commandIntent(request: AiCommandRequest): ResultData<AiCommandIntentResponse> = try {
        val response = client.post("$baseUrl/${EndPoints.aiCommandIntent()}") {
            contentType(ContentType.Application.Json)
            setBody(request.copy(stream = false))
        }
        val body = response.body<AiCommandIntentResponse>()

        if (body.intent != null) {
            ResultData.Complete(body)
        } else {
            ResultData.Error(
                GenAiException(body.error ?: "The AI could not decide what to do (${response.status})")
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    /** Streams the answer of a free command, written for the intent the request carries. */
    fun streamCommand(request: AiCommandRequest): Flow<ResultData<String>> =
        streamFromEndpoint(EndPoints.aiCommand()) { setBody(request.copy(stream = true)) }

    /** Asks the backend to draw a picture for a paragraph. The answer carries its URL. */
    suspend fun generateImage(request: AiImageRequest): ResultData<AiImageResponse> = try {
        val response = client.post("$baseUrl/${EndPoints.aiImage()}") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val body = response.body<AiImageResponse>()

        if (body.imageUrl != null) {
            ResultData.Complete(body)
        } else {
            ResultData.Error(GenAiException(body.error ?: "The AI drew no picture (${response.status})"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    /** Asks the backend to draw a header for a document. The answer carries its CDN URL. */
    suspend fun generateHeaderImage(request: HeaderImageGenerateRequest): ResultData<String> = try {
        val response = client.post("$baseUrl/${EndPoints.aiHeaderImage()}") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val body = response.body<HeaderImageGenerateResponse>()
        val url = body.imageUrl

        if (url != null) {
            ResultData.Complete(url)
        } else {
            ResultData.Error(GenAiException(body.error ?: "The AI drew no picture (${response.status})"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    /** The header pictures of [workspaceId], the newest first. */
    suspend fun headerImages(workspaceId: String): ResultData<List<HeaderImageApi>> = try {
        val response = client.get("$baseUrl/${EndPoints.workspaceHeaderImages(workspaceId)}")

        if (response.status.isSuccess()) {
            ResultData.Complete(response.body<HeaderImagesResponse>().images)
        } else {
            ResultData.Error(GenAiException("The header pictures could not be loaded (${response.status})"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    /** Keeps an approved header with the pictures of [workspaceId]. */
    suspend fun saveHeaderImage(workspaceId: String, imageUrl: String): ResultData<HeaderImageApi> = try {
        val response = client.post("$baseUrl/${EndPoints.workspaceHeaderImages(workspaceId)}") {
            contentType(ContentType.Application.Json)
            setBody(HeaderImageSaveRequest(imageUrl))
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(response.body())
        } else {
            ResultData.Error(GenAiException("The header picture could not be saved (${response.status})"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultData.Error(e)
    }

    /**
     * Shared streaming implementation that delivers results incrementally as they arrive.
     * Uses callbackFlow to safely emit from within the HTTP execute callback,
     * preserving real-time streaming behavior for callers.
     */
    private fun streamFromEndpoint(
        endpoint: String,
        prompt: String,
        model: String?
    ): Flow<ResultData<String>> =
        streamFromEndpoint(endpoint) { setBody(GenAiRequest(prompt, model, stream = true)) }

    /** Streams the SSE answer of [endpoint] to a request whose [body] the caller sets. */
    private fun streamFromEndpoint(
        endpoint: String,
        body: HttpRequestBuilder.() -> Unit
    ): Flow<ResultData<String>> = callbackFlow {
        try {
            client.preparePost {
                url("$baseUrl/$endpoint")
                contentType(ContentType.Application.Json)
                body()
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
                        // Emit immediately for incremental delivery
                        trySend(ResultData.Complete(parsed.response))
                    }

                    if (parsed.done) {
                        break
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trySend(ResultData.Error(e.toException()))
        }

        close()
        awaitClose()
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
