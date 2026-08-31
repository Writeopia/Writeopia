package io.writeopia.persistence.room.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

internal const val TOKEN_ENTITY: String = "TOKEN_ENTITY_TABLE"

@Entity(tableName = TOKEN_ENTITY)
class TokenEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "access_token") val accessToken: String,
    @ColumnInfo(name = "refresh_token") val refreshToken: String? = null,
    @ColumnInfo(name = "access_token_expires_at") val accessTokenExpiresAt: Long? = null
)
