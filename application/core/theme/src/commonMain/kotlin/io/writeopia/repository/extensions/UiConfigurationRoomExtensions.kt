package io.writeopia.repository.extensions

import io.writeopia.model.ColorThemeOption
import io.writeopia.model.Font
import io.writeopia.model.PersistenceMode
import io.writeopia.model.UiConfiguration
import io.writeopia.repository.entity.UiConfigurationEntity

fun UiConfiguration.toRoomEntity() = UiConfigurationEntity(
    userId = userId,
    colorThemeOption = colorThemeOption.theme,
    font = this.font.label,
    persistenceMode = persistenceMode.name
)

fun UiConfigurationEntity.toModel() = UiConfiguration(
    userId = userId,
    colorThemeOption = ColorThemeOption.fromText(colorThemeOption) ?: ColorThemeOption.SYSTEM,
    sideMenuWidth = 500F,
    font = Font.fromLabel(this.font),
    persistenceMode = try { PersistenceMode.valueOf(persistenceMode) } catch (e: Exception) { PersistenceMode.LOCAL_DATABASE }
)
