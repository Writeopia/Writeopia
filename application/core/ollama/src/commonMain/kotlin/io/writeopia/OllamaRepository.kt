package io.writeopia

import io.writeopia.api.OllamaApi
import io.writeopia.persistence.OllamaDao
import io.writeopia.requests.ModelsResponse
import io.writeopia.responses.DownloadModelResponse
import io.writeopia.sdk.ai.AiClient
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

private const val SUGGESTION_PROMPT =
    """
        Generate a list of options.
        Start each options with a line break and "-".
        Generate at most 5 items.
        Use this context to generate the list:
    """

class OllamaRepository(
    private val ollamaApi: OllamaApi,
    private val ollamaDao: OllamaDao?
) : AiClient {

    override suspend fun generateListItems(
        model: String,
        context: String,
        url: String
    ): ResultData<List<String>> {
        val result = ollamaApi.generateReply(model, "$SUGGESTION_PROMPT $context", url)

        return if (result.done == true && result.response?.isNotEmpty() == true) {
            result.response
                .split("\n")
                .filter { line -> line.startsWith("-") }
                .let { list -> ResultData.Complete(list) }
        } else {
            ResultData.Error()
        }
    }

    suspend fun generateReply(model: String, prompt: String, url: String): String {
        return ollamaApi.generateReply(model, prompt, url).response ?: ""
    }

    fun streamReply(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        ollamaApi.streamReply(model, prompt, url)

    fun streamSummary(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        ollamaApi.streamSummary(model, prompt, url)

    fun streamActionsPoints(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        ollamaApi.streamActionsPoints(model, prompt, url)

    fun streamFaq(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        ollamaApi.streamFaq(model, prompt, url)

    fun streamTags(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        ollamaApi.streamTags(model, prompt, url)

    fun listenToModels(url: String): Flow<ResultData<ModelsResponse>> =
        ollamaApi.getModelsAsFlow(url)

    suspend fun getModels(url: String): ResultData<ModelsResponse> = ollamaApi.getModels(url)

    suspend fun saveOllamaUrl(id: String, url: String) {
        ollamaDao?.updateConfiguration(id) {
            this.copy(url = url)
        }

        refreshConfiguration(id)
    }

    suspend fun saveOllamaSelectedModel(id: String, model: String) {
        ollamaDao?.updateConfiguration(id) {
            this.copy(selectedModel = model)
        }

        refreshConfiguration(id)
    }

    override suspend fun getSelectedModel(userId: String): String? =
        ollamaDao?.getConfiguration(userId)?.selectedModel

    fun listenForConfiguration(id: String) =
        ollamaDao?.listenForConfiguration(id) ?: MutableStateFlow(null)

    suspend fun refreshConfiguration(id: String) {
        ollamaDao?.refreshStateOfId(id)
    }

    override suspend fun getConfiguredUrl(id: String): String? =
        ollamaDao?.getConfiguration(id)?.url

    suspend fun deleteModel(model: String, url: String): ResultData<Boolean> =
        ollamaApi.removeModel(model, url)

    fun downloadModel(model: String, url: String): Flow<ResultData<DownloadModelResponse>> =
        ollamaApi.downloadModel(model, url)
}
