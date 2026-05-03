package io.writeopia.persistence.room.extensions

import io.writeopia.persistence.room.data.entities.UiConfigurationRoomEntity
import io.writeopia.repository.entity.UiConfigurationEntity

fun UiConfigurationEntity.toRoom() =
    UiConfigurationRoomEntity(
        userId = userId,
        colorThemeOption = colorThemeOption,
        font = font,
        persistenceMode = persistenceMode
    )

fun UiConfigurationRoomEntity.toCommon() =
    UiConfigurationEntity(
        userId = userId,
        colorThemeOption = colorThemeOption,
        font = font,
        persistenceMode = persistenceMode
    )
