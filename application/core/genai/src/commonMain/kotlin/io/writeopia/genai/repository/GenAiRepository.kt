package io.writeopia.genai.repository

import io.writeopia.sdk.ai.AiClient
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.genai.api.GenAiApi
import io.writeopia.genai.model.GenAiResponse
import kotlinx.coroutines.flow.Flow

private const val SUGGESTION_PROMPT =
    """
        Generate a list of options. Start each options with a line break and "-". Generate at most 5 items. Use this context to generate the list:
    """

class GenAiRepository(
    private val genAiApi: GenAiApi,
    private val defaultModel: String? = null
) : AiClient {

    private var generatingListItems = false

    // url parameter is ignored for GenAI - backend URL is configured in API
    override suspend fun generateListItems(
        model: String,
        context: String,
        url: String
    ): ResultData<List<String>> {
        try {
            if (generatingListItems) return ResultData.Loading()

            generatingListItems = true
            val result = genAiApi.generate("$SUGGESTION_PROMPT $context", model.takeIf { it.isNotBlank() } ?: defaultModel)

            return when (result) {
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
            return ResultData.Error(e)
        } finally {
            generatingListItems = false
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
}
