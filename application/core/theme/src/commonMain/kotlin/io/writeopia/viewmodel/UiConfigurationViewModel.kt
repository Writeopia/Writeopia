package io.writeopia.viewmodel

import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import kotlinx.coroutines.flow.StateFlow

interface UiConfigurationViewModel {

    fun listenForColorTheme(getUserId: suspend () -> String): StateFlow<ColorThemeOption?>

    fun listenForAccentColor(getUserId: suspend () -> String): StateFlow<AccentColor?>

    fun changeColorTheme(colorThemeOption: ColorThemeOption)

    fun changeAccentColor(accentColor: AccentColor)
}
