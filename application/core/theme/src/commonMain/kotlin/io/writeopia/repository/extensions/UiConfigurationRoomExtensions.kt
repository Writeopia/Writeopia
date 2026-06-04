package io.writeopia.repository.extensions

import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.Font
import io.writeopia.model.UiConfiguration
import io.writeopia.repository.entity.UiConfigurationEntity

fun UiConfiguration.toRoomEntity() = UiConfigurationEntity(
    userId = userId,
    colorThemeOption = colorThemeOption.theme,
    accentColor = accentColor.id,
    font = font.label
)

fun UiConfigurationEntity.toModel() = UiConfiguration(
    userId = userId,
    colorThemeOption = ColorThemeOption.fromText(colorThemeOption) ?: ColorThemeOption.SYSTEM,
    accentColor = AccentColor.fromId(accentColor),
    sideMenuWidth = 500F,
    font = Font.fromLabel(this.font)
)
