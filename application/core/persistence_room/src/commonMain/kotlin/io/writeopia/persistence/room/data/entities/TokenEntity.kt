package io.writeopia.persistence.room.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

internal const val TOKEN_ENTITY: String = "TOKEN_ENTITY_TABLE"

@Entity(tableName = TOKEN_ENTITY)
class TokenEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "token") val token: String,
)
