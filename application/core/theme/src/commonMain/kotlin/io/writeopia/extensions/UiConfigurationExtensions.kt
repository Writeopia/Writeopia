package io.writeopia.extensions

import io.writeopia.app.sql.UiConfigurationEntity
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.Font
import io.writeopia.model.UiConfiguration

fun UiConfiguration.toEntity() = UiConfigurationEntity(
    user_id = userId,
    color_theme_option = colorThemeOption.theme,
    accent_color = accentColor.id,
    side_menu_width = sideMenuWidth.toLong(),
    font_family = font.label
)

fun UiConfigurationEntity.toModel() = UiConfiguration(
    userId = user_id,
    colorThemeOption = ColorThemeOption.fromText(color_theme_option) ?: ColorThemeOption.SYSTEM,
    accentColor = AccentColor.fromId(accent_color),
    sideMenuWidth = side_menu_width?.toFloat() ?: 280F,
    font = Font.fromLabel(font_family)
)
