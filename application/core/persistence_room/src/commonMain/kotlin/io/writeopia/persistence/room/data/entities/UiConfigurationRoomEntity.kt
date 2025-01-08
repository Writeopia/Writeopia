package io.writeopia.persistence.room.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

const val UI_CONFIGURATION_TABLE = "UI_CONFIGURATION_TABLE"

@Entity(tableName = UI_CONFIGURATION_TABLE)
class UiConfigurationRoomEntity(
    @PrimaryKey val userId: String,
    val colorThemeOption: String,
    val font: String
)
