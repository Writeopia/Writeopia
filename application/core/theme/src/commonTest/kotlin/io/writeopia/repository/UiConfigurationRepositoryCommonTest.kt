package io.writeopia.repository

import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.UiConfiguration
import kotlin.test.assertEquals

object UiConfigurationRepositoryCommonTest {

    suspend fun shouldBePossibleToSaveAndGetColorTheme(repository: UiConfigurationRepository) {
        val userId = "disconnected_user"
        repository.run {
            val colorTheme = ColorThemeOption.DARK

            this.updateConfiguration(userId) { config ->
                config.copy(colorThemeOption = colorTheme)
            }

            assertEquals(
                colorTheme,
                this.getUiConfigurationEntity(userId)!!.colorThemeOption
            )
        }
    }

    suspend fun shouldBePossibleToKeepChoiceToMoveSideMenu(repository: UiConfigurationRepository) {
        val userId = "disconnected_user"
        val width = 200F

        repository.run {
            insertUiConfiguration(UiConfiguration(userId, ColorThemeOption.SYSTEM, AccentColor.PURPLE, width))
            assertEquals(getUiConfigurationEntity(userId)?.sideMenuWidth, width)
        }
    }
}
