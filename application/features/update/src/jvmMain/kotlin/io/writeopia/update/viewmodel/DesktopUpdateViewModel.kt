package io.writeopia.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.update.DesktopUpdateChecker
import io.writeopia.update.model.DesktopUpdate
import io.writeopia.update.model.DesktopUpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DesktopUpdateUiState {
    data object Checking : DesktopUpdateUiState

    data object Idle : DesktopUpdateUiState

    data object CheckFailed : DesktopUpdateUiState

    data class UpdateAvailable(
        val update: DesktopUpdate,
        val openFailed: Boolean = false
    ) : DesktopUpdateUiState
}

class DesktopUpdateViewModel(
    private val checker: DesktopUpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow<DesktopUpdateUiState>(DesktopUpdateUiState.Checking)
    val uiState: StateFlow<DesktopUpdateUiState> = _uiState.asStateFlow()

    init {
        checkForUpdate()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = when (val result = checker.checkForUpdate()) {
                is DesktopUpdateCheckResult.UpdateAvailable -> {
                    DesktopUpdateUiState.UpdateAvailable(result.update)
                }

                DesktopUpdateCheckResult.NoUpdate -> DesktopUpdateUiState.Idle
                is DesktopUpdateCheckResult.Failure -> DesktopUpdateUiState.CheckFailed
            }
        }
    }

    fun onDownloadOpened() {
        _uiState.value = DesktopUpdateUiState.Idle
    }

    fun onDownloadOpenFailed() {
        val current = _uiState.value as? DesktopUpdateUiState.UpdateAvailable ?: return
        _uiState.value = current.copy(openFailed = true)
    }

    fun dismissUpdate() {
        _uiState.value = DesktopUpdateUiState.Idle
    }

    fun dismissCheckFailure() {
        if (_uiState.value == DesktopUpdateUiState.CheckFailed) {
            _uiState.value = DesktopUpdateUiState.Idle
        }
    }
}
