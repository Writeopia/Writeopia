package io.writeopia.common.utils.persistence.daos

interface TokenCommonDao {

    suspend fun getTokenByUserId(userId: String): String?

    suspend fun saveToken(token: String, userId: String)
}
