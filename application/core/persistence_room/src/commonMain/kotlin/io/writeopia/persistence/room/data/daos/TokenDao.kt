package io.writeopia.persistence.room.data.daos

import androidx.room.Dao
import androidx.room.Query
import io.writeopia.persistence.room.data.entities.TOKEN_ENTITY
import io.writeopia.persistence.room.data.entities.TokenEntity

@Dao
interface TokenDao {

    @Query("SELECT * FROM $TOKEN_ENTITY WHERE user_id = :userId")
    suspend fun getTokenByUserId(userId: String): TokenEntity?

    @Query("INSERT INTO $TOKEN_ENTITY (user_id, token) VALUES (:userId, :token)")
    suspend fun insertToken(userId: String, token: String)
}
