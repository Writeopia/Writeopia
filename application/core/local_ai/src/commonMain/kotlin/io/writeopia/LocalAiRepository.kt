package io.writeopia

import io.writeopia.api.LocalAiApi
import io.writeopia.model.LocalAiConfig
import io.writeopia.persistence.LocalAiDao
import io.writeopia.requests.ModelsResponse
import io.writeopia.responses.DownloadModelResponse
import io.writeopia.sdk.ai.AiClient
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex

private const val SUGGESTION_PROMPT =
    """
        Generate a list of options. Start each options with a line break and "-". Generate at most 5 items. Use this context to generate the list:
    """

class LocalAiRepository(
    private val localAiApi: LocalAiApi,
    private val localAiDao: LocalAiDao?
) : AiClient {

    private val generateListItemsMutex = Mutex()

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
            val result = localAiApi.generateReply(model, "$SUGGESTION_PROMPT $context", url)

            if (result.done == true && result.response?.isNotEmpty() == true) {
                result.response
                    .split("\n")
                    .filter { line -> line.trim().startsWith("-") }
                    .filter { line -> line.isNotEmpty() }
                    .map { line -> line.substring(1).trim() }
                    .let { list ->
                        ResultData.Complete(list)
                    }
            } else {
                ResultData.Error()
            }
        } catch (e: Exception) {
            ResultData.Error(e)
        } finally {
            generateListItemsMutex.unlock()
        }
    }

    suspend fun generateReply(model: String, prompt: String, url: String): String =
        localAiApi.generateReply(model, prompt, url).response ?: ""

    suspend fun generateCompleteSummary(
        model: String,
        prompt: String,
        url: String,
        markdownResult: Boolean = false
    ): String = localAiApi.generateCompleteSummary(model, prompt, url, markdownResult)

    fun streamReply(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        localAiApi.streamReply(model, prompt, url)

    fun streamSummary(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        localAiApi.streamSummary(model, prompt, url)

    fun streamActionsPoints(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        localAiApi.streamActionsPoints(model, prompt, url)

    fun streamFaq(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        localAiApi.streamFaq(model, prompt, url)

    fun streamTags(model: String, prompt: String, url: String): Flow<ResultData<String>> =
        localAiApi.streamTags(model, prompt, url)

    fun listenToModels(url: String): Flow<ResultData<ModelsResponse>> =
        localAiApi.getModelsAsFlow(url)

    suspend fun getModels(url: String): ResultData<ModelsResponse> = localAiApi.getModels(url)

    suspend fun saveLocalAiUrl(id: String, url: String) {
        localAiDao?.updateConfiguration(id) {
            this.copy(url = url)
        }

        refreshConfiguration(id)
    }

    suspend fun saveLocalAiSelectedModel(id: String, model: String) {
        localAiDao?.updateConfiguration(id) {
            this.copy(selectedModel = model)
        }

        refreshConfiguration(id)
    }

    override suspend fun getSelectedModel(userId: String): String? =
        localAiDao?.getConfiguration(userId)?.selectedModel

    fun listenForConfiguration(id: String): StateFlow<LocalAiConfig?> =
        localAiDao?.listenForConfiguration(id) ?: MutableStateFlow(null)

    suspend fun refreshConfiguration(id: String) {
        localAiDao?.refreshStateOfId(id)
    }

    override suspend fun getConfiguredUrl(id: String): String? =
        getLocalAiUrlOverride()
            ?: localAiDao?.getConfiguration(id)?.url
            ?: LocalAiApi.defaultUrl()

    suspend fun deleteModel(model: String, url: String): ResultData<Boolean> =
        localAiApi.removeModel(model, url)

    fun downloadModel(model: String, url: String): Flow<ResultData<DownloadModelResponse>> =
        localAiApi.downloadModel(model, url)

    companion object {
        private val _localAiUrlOverride: String? by lazy {
            try {
                System.getenv("LOCAL_AI_URL")?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }

        fun getLocalAiUrlOverride(): String? = _localAiUrlOverride
    }
}
