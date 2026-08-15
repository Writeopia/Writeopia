package io.writeopia.repository

import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.Font
import io.writeopia.model.UiConfiguration
import kotlinx.browser.localStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class UiConfigurationLocalStorageRepository : UiConfigurationRepository {

    private val configurationState = MutableStateFlow(
        UiConfiguration(
            userId = "disconnected_user",
            colorThemeOption = ColorThemeOption.SYSTEM,
            sideMenuWidth = 280F
        )
    )

    override suspend fun insertUiConfiguration(uiConfiguration: UiConfiguration) {
        saveToLocalStorage(uiConfiguration)
        configurationState.value = uiConfiguration
    }

    override suspend fun getUiConfigurationEntity(userId: String): UiConfiguration {
        return configurationState.value
    }

    override suspend fun updateConfiguration(
        userId: String,
        change: (UiConfiguration) -> UiConfiguration
    ) {
        val updated = configurationState.value.let(change)
        saveToLocalStorage(updated)
        configurationState.value = updated
    }

    override fun listenForUiConfiguration(
        getUserId: String,
        coroutineScope: CoroutineScope
    ): Flow<UiConfiguration?> {
        coroutineScope.launch(Dispatchers.Default) {
            val config = loadFromLocalStorage(getUserId)
            if (config != null) {
                configurationState.value = config
            }
        }

        return configurationState
    }

    private fun saveToLocalStorage(config: UiConfiguration) {
        val prefix = "writeopia_ui_${config.userId}_"
        localStorage.setItem("${prefix}color_theme", config.colorThemeOption.theme)
        localStorage.setItem("${prefix}accent_color", config.accentColor.id)
        localStorage.setItem("${prefix}side_menu_width", config.sideMenuWidth.toString())
        localStorage.setItem("${prefix}font", config.font.label)
    }

    private fun loadFromLocalStorage(userId: String): UiConfiguration? {
        val prefix = "writeopia_ui_${userId}_"
        val colorTheme = localStorage.getItem("${prefix}color_theme")
        val accentColor = localStorage.getItem("${prefix}accent_color")
        val sideMenuWidth = localStorage.getItem("${prefix}side_menu_width")
        val font = localStorage.getItem("${prefix}font")

        // If no data exists, return null
        if (colorTheme == null && accentColor == null && sideMenuWidth == null && font == null) {
            return null
        }

        return UiConfiguration(
            userId = userId,
            colorThemeOption = ColorThemeOption.fromText(colorTheme) ?: ColorThemeOption.SYSTEM,
            accentColor = AccentColor.fromId(accentColor),
            sideMenuWidth = sideMenuWidth?.toFloatOrNull() ?: 280F,
            font = font?.let { runCatching { Font.fromLabel(it) }.getOrNull() } ?: Font.SYSTEM
        )
    }

    companion object {
        private var instance: UiConfigurationLocalStorageRepository? = null

        fun singleton(): UiConfigurationLocalStorageRepository =
            instance ?: UiConfigurationLocalStorageRepository().also {
                instance = it
            }
    }
}
