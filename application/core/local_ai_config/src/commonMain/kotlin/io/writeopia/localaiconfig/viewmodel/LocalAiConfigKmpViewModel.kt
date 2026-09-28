package io.writeopia.localaiconfig.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.LocalAiRepository
import io.writeopia.api.LocalAiAutoConfigApi
import io.writeopia.common.utils.download.DownloadParser
import io.writeopia.common.utils.download.DownloadState
import io.writeopia.controller.LocalAiConfigController
import io.writeopia.model.LocalAiWizardState
import io.writeopia.model.ProviderInfo
import io.writeopia.model.WizardErrorType
import io.writeopia.responses.DownloadModelResponse
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.utils.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A [LocalAiConfigController] that doesn't require a logged-in session - it keys the persisted
 * configuration by a fixed [userId] instead of the current [io.writeopia.sdk.models.user.WriteopiaUser].
 * Used to configure local AI before/without ever signing in (e.g. right after choosing the
 * offline space), where the heavier session-bound implementation in `global_shell` isn't
 * available/appropriate.
 */
class LocalAiConfigKmpViewModel(
    private val userId: String,
    private val localAiRepository: LocalAiRepository,
    private val localAiAutoConfigApi: LocalAiAutoConfigApi,
) : LocalAiConfigController, ViewModel() {

    private val retryModelsTrigger = MutableStateFlow(0)

    private val localAiConfigState = localAiRepository.listenForConfiguration(userId)

    private val _downloadModelState =
        MutableStateFlow<ResultData<DownloadModelResponse>>(ResultData.Idle())

    private val _autoConfigureState = MutableStateFlow<ResultData<Unit>>(ResultData.Idle())
    override val autoConfigureState: StateFlow<ResultData<Unit>> = _autoConfigureState.asStateFlow()

    private val _wizardState = MutableStateFlow<LocalAiWizardState>(LocalAiWizardState.Closed)
    override val wizardState: StateFlow<LocalAiWizardState> = _wizardState.asStateFlow()

    override val downloadModelState: StateFlow<ResultData<DownloadState>> =
        _downloadModelState.map { resultData ->
            resultData.map { response ->
                val completed = DownloadParser.toHumanReadableAmount(response.completed)
                val total = DownloadParser.toHumanReadableAmount(response.total)

                val info = buildString {
                    completed.takeIf { it.isNotEmpty() }?.let { append(it) }
                    total.takeIf { it.isNotEmpty() }?.let { append("/$it") }
                }

                DownloadState(
                    title = response.modelName ?: "",
                    info = info,
                    percentage = response.completed?.toFloat()
                        ?.div(response.total?.toFloat() ?: 1F) ?: 0F
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Idle())

    override val localAiUrl: StateFlow<String> =
        localAiConfigState.map { config ->
            LocalAiRepository.getLocalAiUrlOverride()
                ?: config?.url.takeIf { it?.isNotEmpty() == true }
                ?: ""
        }.stateIn(viewModelScope, SharingStarted.Lazily, LocalAiRepository.getLocalAiUrlOverride() ?: "")

    override val localAiSelectedModelState: StateFlow<String> = localAiConfigState
        .map { config -> config?.selectedModel ?: "" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    @OptIn(ExperimentalCoroutinesApi::class)
    override val modelsForUrl: StateFlow<ResultData<List<String>>> =
        combine(localAiUrl, retryModelsTrigger) { url, _ -> url }
            .flatMapLatest { url -> localAiRepository.listenToModels(url) }
            .map { result ->
                result.map { modelResponse ->
                    modelResponse.models
                        .map { it.model }
                        .takeIf { it.isNotEmpty() }
                        ?: listOf("No models found")
                }
            }
            .onEach { modelsResult ->
                if (modelsResult is ResultData.Complete && modelsResult.data.size == 1) {
                    selectLocalAiModel(modelsResult.data.first())
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Idle())

    override fun changeLocalAiUrl(url: String) {
        viewModelScope.launch(Dispatchers.Default) {
            localAiRepository.saveLocalAiUrl(userId, url)
        }
    }

    override fun selectLocalAiModel(model: String) {
        viewModelScope.launch(Dispatchers.Default) {
            localAiRepository.saveLocalAiSelectedModel(userId, model)
            localAiRepository.refreshConfiguration(userId)
        }
    }

    override fun retryModels() {
        retryModelsTrigger.value++
    }

    override fun modelToDownload(model: String, onComplete: () -> Unit) {
        if (model.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val url = localAiRepository.getConfiguredUrl(userId)?.trim()

            if (url != null) {
                localAiRepository.downloadModel(model, url)
                    .collectLatest { result ->
                        _downloadModelState.value = result

                        if (result is ResultData.Complete) {
                            retryModels()
                            onComplete()

                            val modelsResult = localAiRepository.getModels(url)

                            if (
                                modelsResult is ResultData.Complete &&
                                modelsResult.data.models.size == 1
                            ) {
                                localAiRepository.saveLocalAiSelectedModel(
                                    userId,
                                    modelsResult.data.models.first().model
                                )
                                localAiRepository.refreshConfiguration(userId)
                            }
                        }
                    }
            }
        }
    }

    override fun deleteModel(model: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val url = localAiRepository.getConfiguredUrl(userId)?.trim()

            if (url != null) {
                localAiRepository.deleteModel(model, url)
                retryModels()
            }
        }
    }

    override fun autoConfigure() {
        viewModelScope.launch(Dispatchers.Default) {
            _autoConfigureState.value = ResultData.Loading()

            when (val configResult = localAiAutoConfigApi.getAutoConfig()) {
                is ResultData.Complete -> {
                    val config = configResult.data
                    var workingUrl: String? = null

                    for (candidateUrl in listOf(config.ollamaUrl, config.llmmanUrl)) {
                        if (localAiRepository.getModels(candidateUrl) is ResultData.Complete) {
                            workingUrl = candidateUrl
                            break
                        }
                    }

                    if (workingUrl == null) {
                        _autoConfigureState.value = ResultData.Error(
                            Exception(
                                "Local AI was not found running on this machine. " +
                                    "Please, install and start Ollama or llmman and try again."
                            )
                        )
                        return@launch
                    }

                    localAiRepository.saveLocalAiUrl(userId, workingUrl)

                    val defaultModel = config.modelTiers[config.defaultTierIndex].modelName
                    localAiRepository.downloadModel(defaultModel, workingUrl)
                        .collectLatest { result ->
                            _downloadModelState.value = result

                            when (result) {
                                is ResultData.Complete -> {
                                    localAiRepository.saveLocalAiSelectedModel(userId, defaultModel)
                                    localAiRepository.refreshConfiguration(userId)
                                    retryModels()
                                    _autoConfigureState.value = ResultData.Complete(Unit)
                                }

                                is ResultData.Error -> {
                                    _autoConfigureState.value = ResultData.Error(result.exception)
                                }

                                else -> {}
                            }
                        }
                }

                is ResultData.Error -> {
                    _autoConfigureState.value = ResultData.Error(configResult.exception)
                }

                else -> {}
            }
        }
    }

    override fun openWizard() {
        viewModelScope.launch(Dispatchers.Default) {
            _wizardState.value = LocalAiWizardState.DetectingProviders

            when (val configResult = localAiAutoConfigApi.getAutoConfig()) {
                is ResultData.Complete -> {
                    val config = configResult.data
                    val providers = mutableListOf<ProviderInfo>()

                    val ollamaAvailable =
                        localAiRepository.getModels(config.ollamaUrl) is ResultData.Complete
                    providers.add(
                        ProviderInfo(name = "Ollama", url = config.ollamaUrl, isAvailable = ollamaAvailable)
                    )

                    val llmmanAvailable =
                        localAiRepository.getModels(config.llmmanUrl) is ResultData.Complete
                    providers.add(
                        ProviderInfo(name = "llmman", url = config.llmmanUrl, isAvailable = llmmanAvailable)
                    )

                    _wizardState.value = if (!ollamaAvailable && !llmmanAvailable) {
                        LocalAiWizardState.Error(WizardErrorType.NO_PROVIDER_DETECTED)
                    } else {
                        LocalAiWizardState.SelectingConfiguration(
                            config = config,
                            availableProviders = providers
                        )
                    }
                }

                is ResultData.Error -> {
                    _wizardState.value = LocalAiWizardState.Error(WizardErrorType.FETCH_CONFIG_FAILED)
                }

                else -> {}
            }
        }
    }

    override fun selectProviderAndModel(providerUrl: String, modelName: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _wizardState.value = LocalAiWizardState.Closed

            localAiRepository.saveLocalAiUrl(userId, providerUrl)
            localAiRepository.saveLocalAiSelectedModel(userId, modelName)
            localAiRepository.refreshConfiguration(userId)

            localAiRepository.downloadModel(modelName, providerUrl)
                .collectLatest { result ->
                    _downloadModelState.value = result

                    when (result) {
                        is ResultData.Complete -> retryModels()
                        is ResultData.Error -> {
                            _wizardState.value = LocalAiWizardState.Error(WizardErrorType.DOWNLOAD_FAILED)
                        }
                        else -> {}
                    }
                }
        }
    }

    override fun closeWizard() {
        _wizardState.value = LocalAiWizardState.Closed
    }
}
