package io.writeopia.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
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
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.requests.DeleteModelRequest
import io.writeopia.requests.DownloadModelRequest
import io.writeopia.requests.ModelsResponse
import io.writeopia.requests.LocalAiGenerateRequest
import io.writeopia.responses.DownloadModelResponse
import io.writeopia.responses.LocalAiResponse
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

private const val SUMMARY_PROMPT =
    "A user has made a request. Summarize the following text while preserving its key points and main ideas. Use at most 12 lines. Keep the summary concise and clear. If the text contains multiple sections, highlight the most important aspects of each. Maintain the original tone and intent where possible. Detect the language of the text and write in the same language"
private const val ACTIONS_POINTS_PROMPT =
    "A user has made a request. Extract key action points from the following text. Create just an introduction and the list of action items with at most 10. Don't add conclusions or introductions. Use the language of the text"
private const val FAQ_PROMPT =
    "A user has made a request. Generate a list of frequently asked questions (FAQs) based on the following text. Include clear and concise answers that help users understand key points. Prioritize the most relevant and common concerns. Detect the language of the text and write in the same language"
private const val TAGS_PROMPT =
    "Generate a list of relevant tags based on the following text. The tags should capture key topics, themes, and important concepts. Use concise, single-word tags that accurately represent the content. Produce at most 10 tags. Detect the language of the text and write in the same language. "
private const val SUMMARY_PROMPT_COMPLETE =
    "Summarize the following text while preserving its key points and main ideas. Keep the summary concise and clear. If the text contains multiple sections, highlight the most important aspects of each. Maintain the original tone and intent where possible. Detect the language of the text and write in the same language."

private const val MARKDOWN_RESULT =
    "Your return should be in valid Markdown format."

/**
 * API for calling LocalAi
 */
class LocalAiApi(
    private val client: HttpClient,
    private val json: Json
) {

    private val generateReplyMutex = Mutex()

    suspend fun generateReply(
        model: String,
        prompt: String,
        url: String
    ): LocalAiResponse = generateReplyMutex.withLock {
        client.post("$url/api/${EndPoints.localAiGenerate()}") {
            contentType(ContentType.Application.Json)
            setBody(LocalAiGenerateRequest(model, prompt, false))
        }.body<LocalAiResponse>()
    }

    fun downloadModel(
        model: String,
        url: String,
    ): Flow<ResultData<DownloadModelResponse>> = flow<ResultData<DownloadModelResponse>> {
        client.preparePost {
            url("$url/api/pull")
            contentType(ContentType.Application.Json)
            setBody(DownloadModelRequest(model))
        }.execute { response ->
            val channel = response.body<ByteReadChannel>()

            while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                val line = channel.readUTF8Line()
                    ?.takeUnless { it.isEmpty() }
                    ?: continue

                val parsed: DownloadModelResponse =
                    json.decodeFromString<DownloadModelResponse>(line)
                        .copy(modelName = model)

                if (parsed.error?.isNotEmpty() == true) {
                    throw LocalAiException("Error - ${parsed.error}")
                }

                emit(ResultData.InProgress(parsed))

                if (parsed.status == "success") {
                    emit(ResultData.Complete(parsed))
                    break
                }
            }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(ResultData.Error<DownloadModelResponse>(e.toException()))
    }

    suspend fun removeModel(
        model: String,
        url: String
    ): ResultData<Boolean> {
        try {
            val isSuccess = client.delete("$url/api/delete") {
                contentType(ContentType.Application.Json)
                setBody(DeleteModelRequest(model.trim()))
            }
                .status
                .isSuccess()

            return ResultData.Complete(isSuccess)
        } catch (e: Exception) {
            return ResultData.Error(e)
        }
    }

    /**
     * Streams reply from LocalAi, delivering results incrementally as they arrive.
     * Uses callbackFlow to safely emit from within the HTTP execute callback,
     * preserving real-time streaming behavior for callers.
     */
    fun streamReply(
        model: String,
        prompt: String,
        url: String
    ): Flow<ResultData<String>> = callbackFlow {
        try {
            client.preparePost {
                url("$url/api/${EndPoints.localAiGenerate()}")
                contentType(ContentType.Application.Json)
                setBody(LocalAiGenerateRequest(model, prompt, true))
            }.execute { response ->
                val stringBuilder = StringBuilder()
                val channel = response.body<ByteReadChannel>()

                while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                    val line = channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                    val value: LocalAiResponse = json.decodeFromString(line)

                    if (value.error?.isNotEmpty() == true) {
                        throw LocalAiException(value.error)
                    }

                    stringBuilder.append(value.response)

                    // Emit immediately for incremental delivery
                    trySend(ResultData.Complete(stringBuilder.toString()))
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

    fun streamSummary(
        model: String,
        prompt: String,
        url: String
    ): Flow<ResultData<String>> =
        streamReply(model, "$SUMMARY_PROMPT:\n ```\n$prompt\n", url)

    fun streamActionsPoints(
        model: String,
        prompt: String,
        url: String
    ): Flow<ResultData<String>> =
        streamReply(model, "$ACTIONS_POINTS_PROMPT:\n ```\n$prompt\n", url)

    fun streamFaq(
        model: String,
        prompt: String,
        url: String
    ): Flow<ResultData<String>> =
        streamReply(model, "$FAQ_PROMPT:\n ```\n$prompt\n", url)

    fun streamTags(
        model: String,
        prompt: String,
        url: String
    ): Flow<ResultData<String>> =
        streamReply(model, "$TAGS_PROMPT:\n ```\n$prompt\n", url)

    suspend fun generateCompleteSummary(
        model: String,
        prompt: String,
        url: String,
        markdownResult: Boolean = false,
    ): String {
        val finalPrompt = buildString {
            append(SUMMARY_PROMPT_COMPLETE)
            if (markdownResult) append(" $MARKDOWN_RESULT")
            append(": \n ```\n$prompt\n```")
        }

        return generateReply(model, finalPrompt, url).response ?: ""
    }

    fun getModelsAsFlow(url: String): Flow<ResultData<ModelsResponse>> = flow<ResultData<ModelsResponse>> {
        emit(ResultData.Loading())

        val request = client.get("${url.trim()}/${EndPoints.localAiModels()}") {
            contentType(ContentType.Application.Json)
        }

        emit(ResultData.Complete(request.body()))
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(ResultData.Error<ModelsResponse>(e.toException()))
    }

    suspend fun getModels(url: String): ResultData<ModelsResponse> =
        try {
            val request = client.get("${url.trim()}/${EndPoints.localAiModels()}") {
                contentType(ContentType.Application.Json)
            }

            ResultData.Complete(request.body())
        } catch (e: Exception) {
            ResultData.Error(e)
        }

    companion object {
        fun defaultUrl() = "http://localhost:11434"

        /**
         * Base URL of llmman (https://github.com/llmmanorg/llmman), a local model runner
         * that serves the Ollama API on port 17434. Everything in this class works
         * unchanged against it; only the port differs.
         */
        fun llmmanUrl() = "http://localhost:17434"
    }
}

/**
 * Exception for LocalAi API errors returned in the response body.
 */
class LocalAiException(message: String) : Exception(message)

/**
 * Converts a Throwable to an Exception for use with ResultData.Error.
 */
private fun Throwable.toException(): Exception =
    this as? Exception ?: Exception(this.message, this)
