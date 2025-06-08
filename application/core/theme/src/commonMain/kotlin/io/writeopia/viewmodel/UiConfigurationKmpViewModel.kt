package io.writeopia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.model.ColorThemeOption
import io.writeopia.repository.UiConfigurationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UiConfigurationKmpViewModel(
    private val uiConfigurationSqlDelightRepository: UiConfigurationRepository,
) : ViewModel(), UiConfigurationViewModel {

    override fun listenForColorTheme(
        getUserId: suspend () -> String
    ): StateFlow<ColorThemeOption?> =
        // Todo: Add support for multiple configurations per user in a later moment
        uiConfigurationSqlDelightRepository.listenForUiConfiguration("disconnected_user", viewModelScope)
            .map { uiConfiguration ->
                uiConfiguration?.colorThemeOption ?: ColorThemeOption.SYSTEM
            }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    override fun changeColorTheme(colorThemeOption: ColorThemeOption) {
        viewModelScope.launch(Dispatchers.Default) {
            uiConfigurationSqlDelightRepository
                .updateConfiguration("disconnected_user") { config ->
                    config.copy(colorThemeOption = colorThemeOption)
                }
        }
    }
}
