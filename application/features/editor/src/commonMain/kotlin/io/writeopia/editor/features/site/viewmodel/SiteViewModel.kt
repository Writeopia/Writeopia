package io.writeopia.editor.features.site.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SiteUiState {
    data object Loading : SiteUiState()

    data class Success(val document: Document) : SiteUiState()

    data object Error : SiteUiState()

    data object NotFound : SiteUiState()
}

class SiteViewModel(
    private val documentsApi: DocumentsApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<SiteUiState>(SiteUiState.Loading)
    val uiState: StateFlow<SiteUiState> = _uiState.asStateFlow()

    fun loadDocument(documentId: String) {
        viewModelScope.launch {
            _uiState.value = SiteUiState.Loading

            when (val result = documentsApi.getPublishedDocument(documentId)) {
                is ResultData.Complete -> {
                    _uiState.value = SiteUiState.Success(result.data)
                }
                is ResultData.Error -> {
                    _uiState.value = SiteUiState.Error
                }
                is ResultData.Idle -> {
                    _uiState.value = SiteUiState.Loading
                }
                is ResultData.Loading -> {
                    _uiState.value = SiteUiState.Loading
                }
                is ResultData.InProgress -> {
                    _uiState.value = SiteUiState.Success(result.data)
                }
            }
        }
    }
}
