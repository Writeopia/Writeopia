package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.ai.AiClients
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The AI settings of the phones: which provider answers the AI commands. */
class AiSettingsViewModel(
    private val aiClients: AiClients,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _aiProvider = MutableStateFlow(aiClients.defaultProvider)
    val aiProvider: StateFlow<AiProvider> = _aiProvider.asStateFlow()

    private val _aiProviderChoices = MutableStateFlow<List<AiProvider>>(emptyList())
    val aiProviderChoices: StateFlow<List<AiProvider>> = _aiProviderChoices.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            _aiProvider.value = aiClients.selectedProvider(authRepository.getUser().id)
            _aiProviderChoices.value = aiClients.offeredProviders()
            _isOnline.value = aiClients.isCloudAiReady()
        }
    }

    fun selectAiProvider(provider: AiProvider) {
        _aiProvider.value = provider

        viewModelScope.launch(Dispatchers.Default) {
            aiClients.selectProvider(authRepository.getUser().id, provider)
        }
    }
}
