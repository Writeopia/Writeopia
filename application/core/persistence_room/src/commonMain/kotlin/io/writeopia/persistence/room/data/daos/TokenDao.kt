package io.writeopia.persistence.room.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.writeopia.persistence.room.data.entities.TOKEN_ENTITY
import io.writeopia.persistence.room.data.entities.TokenEntity

@Dao
interface TokenDao {

    @Query("SELECT * FROM $TOKEN_ENTITY WHERE user_id = :userId")
    suspend fun getTokenByUserId(userId: String): TokenEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(tokenEntity: TokenEntity)
}
