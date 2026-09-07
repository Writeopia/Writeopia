package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.account.ui.CloudAiUsageState
import io.writeopia.genai.api.GenAiApi
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CloudAiUsageViewModel(
    private val genAiApi: GenAiApi?
) : ViewModel() {

    private val _usageState = MutableStateFlow<CloudAiUsageState>(CloudAiUsageState.Loading)
    val usageState: StateFlow<CloudAiUsageState> = _usageState.asStateFlow()

    init {
        loadUsage()
    }

    fun loadUsage() {
        val api = genAiApi
        if (api == null) {
            _usageState.value = CloudAiUsageState.Error("Cloud AI is not configured")
            return
        }

        viewModelScope.launch {
            _usageState.value = CloudAiUsageState.Loading

            when (val result = api.getUsage()) {
                is ResultData.Complete -> {
                    _usageState.value = CloudAiUsageState.Success(result.data)
                }
                is ResultData.Error -> {
                    _usageState.value = CloudAiUsageState.Error(
                        result.exception?.message ?: "Unknown error"
                    )
                }
                is ResultData.Loading, is ResultData.Idle, is ResultData.InProgress -> {
                    // Keep loading state
                }
            }
        }
    }

    fun retry() {
        loadUsage()
    }
}
