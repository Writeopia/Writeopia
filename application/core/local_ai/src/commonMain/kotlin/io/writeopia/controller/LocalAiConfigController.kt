package io.writeopia.controller

import io.writeopia.common.utils.download.DownloadState
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.StateFlow

interface LocalAiConfigController {

    val localAiSelectedModelState: StateFlow<String>

    val localAiUrl: StateFlow<String>

    val modelsForUrl: StateFlow<ResultData<List<String>>>

    val downloadModelState: StateFlow<ResultData<DownloadState>>

    val autoConfigureState: StateFlow<ResultData<Unit>>

    fun changeLocalAiUrl(url: String)

    fun selectLocalAiModel(model: String)

    fun retryModels()

    fun modelToDownload(model: String, onComplete: () -> Unit = {})

    fun deleteModel(model: String)

    /**
     * Fetches the Local AI configuration from the backend, detects whether Ollama or llmman is
     * running locally, configures the matching URL and downloads and selects the default model.
     */
    fun autoConfigure()
}
