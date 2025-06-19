package io.writeopia.persistence.room.data.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import io.writeopia.persistence.room.data.entities.USER_ENTITY
import io.writeopia.persistence.room.data.entities.UserEntity

@Dao
interface UserDao {

    @Query("SELECT * FROM $USER_ENTITY WHERE user_id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Upsert
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM $USER_ENTITY WHERE selected = 1")
    suspend fun getCurrentUser(): UserEntity?
}
