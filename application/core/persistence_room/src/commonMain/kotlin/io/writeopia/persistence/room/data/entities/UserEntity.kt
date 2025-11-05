package io.writeopia.persistence.room.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

internal const val USER_ENTITY: String = "USER_ENTITY_TABLE"

@Entity(tableName = USER_ENTITY)
class UserEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "selected") val selected: Boolean,
    @ColumnInfo(name = "tier") val tier: String,
)
