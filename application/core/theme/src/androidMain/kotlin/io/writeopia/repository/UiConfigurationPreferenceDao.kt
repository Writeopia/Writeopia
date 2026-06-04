package io.writeopia.repository

import android.content.SharedPreferences
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.Font
import io.writeopia.repository.entity.UiConfigurationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

private const val COLOR_THEME_OPTION = "colorThemeOption"
private const val ACCENT_COLOR_OPTION = "accentColorOption"
private const val FONT_OPTION = "fontOptions"

class UiConfigurationPreferenceDao(
    private val sharedPreferences: SharedPreferences
) : UiConfigurationDao {

    private val themeState = MutableStateFlow(
        UiConfigurationEntity(
            "disconnected_user",
            ColorThemeOption.SYSTEM.theme,
            AccentColor.PURPLE.id,
            Font.SYSTEM.label
        )
    )

    override suspend fun saveUiConfiguration(configuration: UiConfigurationEntity) {
        sharedPreferences.edit()
            .putString(COLOR_THEME_OPTION, configuration.colorThemeOption)
            .putString(ACCENT_COLOR_OPTION, configuration.accentColor)
            .putString(FONT_OPTION, configuration.font)
            .apply()

        themeState.value = configuration
    }

    override suspend fun getConfigurationByUserId(userId: String): UiConfigurationEntity =
        UiConfigurationEntity(
            userId = userId,
            colorThemeOption = sharedPreferences.getString(
                COLOR_THEME_OPTION,
                ColorThemeOption.SYSTEM.theme
            ) ?: ColorThemeOption.SYSTEM.theme,
            accentColor = sharedPreferences.getString(
                ACCENT_COLOR_OPTION,
                AccentColor.PURPLE.id
            ) ?: AccentColor.PURPLE.id,
            font = sharedPreferences.getString(
                FONT_OPTION,
                Font.SYSTEM.label
            ) ?: Font.SYSTEM.label
        )

    override fun listenForConfigurationByUserId(userId: String): Flow<UiConfigurationEntity?> {
        runBlocking {
            getConfigurationByUserId(userId).let {
                themeState.value = it
            }
        }

        return themeState.asStateFlow()
    }
}
