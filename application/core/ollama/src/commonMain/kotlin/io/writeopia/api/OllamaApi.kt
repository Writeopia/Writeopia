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
import io.writeopia.requests.OllamaGenerateRequest
import io.writeopia.responses.DownloadModelResponse
import io.writeopia.responses.OllamaResponse
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

private const val SUMMARY_PROMPT =
    "A user has made a request. Summarize the following text while preserving its key points and main ideas. Use at most 12 lines. Keep the summary concise and clear. If the text contains multiple sections, highlight the most important aspects of each. Maintain the original tone and intent where possible. Detect the language of the text and write in the same language"
private const val ACTIONS_POINTS_PROMPT =
    "A user has made a request. Extract key action points from the following text. Create just an introduction and the list of action items with at most 10. Don't add conclusions or introductions. Use the language of the text"
private const val FAQ_PROMPT =
    "A user has made a request. Generate a list of frequently asked questions (FAQs) based on the following text. Include clear and concise answers that help users understand key points. Prioritize the most relevant and common concerns. Detect the language of the text and write in the same language"
private const val TAGS_PROMPT =
    "Generate a list of relevant tags based on the following text. The tags should capture key topics, themes, and important concepts. Use concise, single-word tags that accurately represent the content. Produce at most 10 tags. Detect the language of the text and write in the same language. "
private const val SUMMARY_PROMPT_COMPLETE =
    "Summarize the following text while preserving its key points and main ideas. Keep the summary concise and clear. If the text contains multiple sections, highlight the most important aspects of each. Maintain the original tone and intent where possible"

/**
 * API for calling Ollama
 */
class OllamaApi(
    private val client: HttpClient,
    private val json: Json
) {

    suspend fun generateReply(
        model: String,
        prompt: String,
        url: String
    ): OllamaResponse =
        client.post("$url/api/${EndPoints.ollamaGenerate()}") {
            contentType(ContentType.Application.Json)
            setBody(OllamaGenerateRequest(model, prompt, false))
        }.body<OllamaResponse>()

    fun downloadModel(
        model: String,
        url: String,
    ): Flow<ResultData<DownloadModelResponse>> = flow {
        try {
            client.preparePost {
                url("$url/api/pull")
                contentType(ContentType.Application.Json)
                setBody(DownloadModelRequest(model))
            }.execute { response ->
                try {
                    val channel = response.body<ByteReadChannel>()

                    while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line()
                            ?.takeUnless { it.isEmpty() }
                            ?: continue

                        val parsed: DownloadModelResponse =
                            json.decodeFromString<DownloadModelResponse>(line)
                                .copy(modelName = model)

                        if (parsed.error?.isNotEmpty() == true) {
                            emit(ResultData.Error(RuntimeException("Error - ${parsed.error}")))
                            break
                        }

                        emit(ResultData.InProgress(parsed))

                        if (parsed.status == "success") {
                            emit(ResultData.Complete(parsed))
                            break
                        }
                    }
                } catch (e: Exception) {
                    emit(ResultData.Error(e))
                }
            }
        } catch (e: Exception) {
            emit(ResultData.Error(e))
        }
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

    fun streamReply(
        model: String,
        prompt: String,
        url: String
    ): Flow<ResultData<String>> =
        flow {
            try {
                client.preparePost {
                    url("$url/api/${EndPoints.ollamaGenerate()}")
                    contentType(ContentType.Application.Json)
                    setBody(OllamaGenerateRequest(model, prompt, true))
                }.execute { response ->
                    try {
                        val stringBuilder = StringBuilder()
                        val channel = response.body<ByteReadChannel>()

                        while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                            val line =
                                channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                            val value: OllamaResponse = json.decodeFromString(line)

                            if (value.error?.isNotEmpty() == true) {
                                emit(ResultData.Error(RuntimeException(value.error)))
                                break
                            }

                            stringBuilder.append(value.response)

                            emit(ResultData.Complete(stringBuilder.toString()))
                        }
                    } catch (e: Exception) {
                        emit(ResultData.Error(e))
                    }
                }
            } catch (e: Exception) {
                emit(ResultData.Error(e))
            }
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

    fun getModelsAsFlow(url: String): Flow<ResultData<ModelsResponse>> {
        return flow {
            try {
                emit(ResultData.Loading())

                val request = client.get("${url.trim()}/${EndPoints.ollamaModels()}") {
                    contentType(ContentType.Application.Json)
                }

                emit(ResultData.Complete(request.body()))
            } catch (e: Exception) {
                emit(ResultData.Error(e))
            }
        }
    }

    suspend fun getModels(url: String): ResultData<ModelsResponse> =
        try {
            val request = client.get("${url.trim()}/${EndPoints.ollamaModels()}") {
                contentType(ContentType.Application.Json)
            }

            ResultData.Complete(request.body())
        } catch (e: Exception) {
            ResultData.Error(e)
        }

    companion object {
        fun defaultUrl() = "http://localhost:11434"
    }
}
