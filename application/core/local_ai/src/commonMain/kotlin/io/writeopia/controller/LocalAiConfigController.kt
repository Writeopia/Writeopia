package io.writeopia.controller

import io.writeopia.common.utils.download.DownloadState
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.StateFlow

interface LocalAiConfigController {

    val localAiSelectedModelState: StateFlow<String>

    val localAiUrl: StateFlow<String>

    val modelsForUrl: StateFlow<ResultData<List<String>>>

    val downloadModelState: StateFlow<ResultData<DownloadState>>

    fun changeLocalAiUrl(url: String)

    fun selectLocalAiModel(model: String)

    fun retryModels()

    fun modelToDownload(model: String, onComplete: () -> Unit = {})

    fun deleteModel(model: String)
}
