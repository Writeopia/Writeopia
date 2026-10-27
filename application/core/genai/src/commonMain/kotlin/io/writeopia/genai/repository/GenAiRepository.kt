package io.writeopia.genai.repository

import io.writeopia.app.dto.ai.AiCommandIntentResponse
import io.writeopia.app.dto.ai.AiCommandRequest
import io.writeopia.app.dto.ai.AiImageRequest
import io.writeopia.sdk.serialization.request.HeaderImageGenerateRequest
import io.writeopia.sdk.ai.AiClient
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.genai.api.GenAiApi
import io.writeopia.genai.model.GenAiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex

private const val SUGGESTION_PROMPT =
    """
        Generate a list of options. Start each options with a line break and "-". Generate at most 5 items. Use this context to generate the list:
    """

class GenAiRepository(
    private val genAiApi: GenAiApi,
    private val defaultModel: String? = null
) : AiClient {

    private val generateListItemsMutex = Mutex()

    // url parameter is ignored for GenAI - backend URL is configured in API
    override suspend fun generateListItems(
        model: String,
        context: String,
        url: String
    ): ResultData<List<String>> {
        // Use tryLock to return Loading if another call is in progress
        if (!generateListItemsMutex.tryLock()) {
            return ResultData.Loading()
        }

        return try {
            val result = genAiApi.generate(
                "$SUGGESTION_PROMPT $context",
                model.takeIf { it.isNotBlank() } ?: defaultModel
            )

            when (result) {
                is ResultData.Complete -> {
                    val response = result.data
                    if (response.response?.isNotEmpty() == true) {
                        response.response
                            .split("\n")
                            .filter { line -> line.trim().startsWith("-") }
                            .filter { line -> line.isNotEmpty() }
                            .map { line -> line.trimStart().removePrefix("-").trim() }
                            .let { list ->
                                ResultData.Complete(list)
                            }
                    } else {
                        ResultData.Error()
                    }
                }
                is ResultData.Error -> ResultData.Error(result.exception)
                else -> ResultData.Error()
            }
        } catch (e: Exception) {
            ResultData.Error(e)
        } finally {
            generateListItemsMutex.unlock()
        }
    }

    override suspend fun getSelectedModel(userId: String): String? {
        // GenAI model is configured on the backend
        return defaultModel
    }

    override suspend fun getConfiguredUrl(id: String): String? {
        // URL is configured in GenAiApi - not user-configurable
        return null
    }

    suspend fun checkStatus(): ResultData<Boolean> = genAiApi.checkStatus()

    suspend fun generate(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        genAiApi.generate(prompt, model ?: defaultModel)

    fun streamGenerate(prompt: String, model: String? = null): Flow<ResultData<String>> =
        genAiApi.streamGenerate(prompt, model ?: defaultModel)

    suspend fun generateSummary(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        genAiApi.generateSummary(prompt, model ?: defaultModel)

    fun streamSummary(prompt: String, model: String? = null): Flow<ResultData<String>> =
        genAiApi.streamSummary(prompt, model ?: defaultModel)

    suspend fun generateActionPoints(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        genAiApi.generateActionPoints(prompt, model ?: defaultModel)

    fun streamActionPoints(prompt: String, model: String? = null): Flow<ResultData<String>> =
        genAiApi.streamActionPoints(prompt, model ?: defaultModel)

    suspend fun generateFaq(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        genAiApi.generateFaq(prompt, model ?: defaultModel)

    fun streamFaq(prompt: String, model: String? = null): Flow<ResultData<String>> =
        genAiApi.streamFaq(prompt, model ?: defaultModel)

    suspend fun generateTags(prompt: String, model: String? = null): ResultData<GenAiResponse> =
        genAiApi.generateTags(prompt, model ?: defaultModel)

    fun streamTags(prompt: String, model: String? = null): Flow<ResultData<String>> =
        genAiApi.streamTags(prompt, model ?: defaultModel)

    /** Whether a free command replaces the text it is about, or adds to it. */
    suspend fun commandIntent(request: AiCommandRequest): ResultData<AiCommandIntentResponse> =
        genAiApi.commandIntent(request.copy(model = request.model ?: defaultModel))

    /** The answer of a free command, streamed. */
    fun streamCommand(request: AiCommandRequest): Flow<ResultData<String>> =
        genAiApi.streamCommand(request.copy(model = request.model ?: defaultModel))

    /**
     * A picture for [paragraph], drawn by the backend with the whole document as context. The
     * result is the URL of the picture, already stored with the user's media.
     */
    suspend fun generateImage(
        paragraph: String,
        documentTitle: String,
        documentText: String
    ): ResultData<String> =
        when (val result = genAiApi.generateImage(AiImageRequest(paragraph, documentTitle, documentText))) {
            is ResultData.Complete -> {
                val url = result.data.imageUrl
                if (url != null) {
                    ResultData.Complete(url)
                } else {
                    ResultData.Error(Exception(result.data.error ?: "The AI drew no picture"))
                }
            }
            is ResultData.Error -> ResultData.Error(result.exception)
            else -> ResultData.Error()
        }

    /**
     * A header picture for a document of [workspaceId], drawn by the backend and served by the
     * CDN. It costs tokens of the user. The result is the URL of the picture.
     */
    suspend fun generateHeaderImage(
        workspaceId: String,
        documentTitle: String,
        documentText: String
    ): ResultData<String> =
        genAiApi.generateHeaderImage(HeaderImageGenerateRequest(workspaceId, documentTitle, documentText))

    /** The URLs of the header pictures of [workspaceId], the newest first. */
    suspend fun headerImages(workspaceId: String): ResultData<List<String>> =
        when (val result = genAiApi.headerImages(workspaceId)) {
            is ResultData.Complete -> ResultData.Complete(result.data.map { image -> image.url })
            is ResultData.Error -> ResultData.Error(result.exception)
            else -> ResultData.Error()
        }

    /** Keeps an approved header picture with the pictures of [workspaceId]. */
    suspend fun saveHeaderImage(workspaceId: String, imageUrl: String): ResultData<Unit> =
        when (val result = genAiApi.saveHeaderImage(workspaceId, imageUrl)) {
            is ResultData.Complete -> ResultData.Complete(Unit)
            is ResultData.Error -> ResultData.Error(result.exception)
            else -> ResultData.Error()
        }
}
